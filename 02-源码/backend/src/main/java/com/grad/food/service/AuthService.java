package com.grad.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.grad.food.common.BizException;
import com.grad.food.common.JwtUtil;
import com.grad.food.common.UserContext;
import com.grad.food.dto.LoginDTO;
import com.grad.food.dto.PasswordDTO;
import com.grad.food.dto.ProfileDTO;
import com.grad.food.entity.*;
import com.grad.food.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 认证与个人中心。
 * 登录失败按账号累计，达到阈值锁定一段时间；任何返回体都不含 password 字段。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysOrgMapper orgMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMessageMapper messageMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LogService logService;

    @Value("${app.login-fail-limit:5}")
    private int failLimit;
    @Value("${app.login-lock-minutes:10}")
    private int lockMinutes;

    /** 登录：校验锁定状态 → 校验密码 → 失败累加 → 成功后清零并发令牌 */
    @Transactional
    public Map<String, Object> login(LoginDTO dto, String ip) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername().trim()));
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已停用，请联系管理员");
        }
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(LocalDateTime.now())) {
            throw new BizException("账号已锁定，请于 " + user.getLockUntil() + " 后重试");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            int fails = (user.getFailCount() == null ? 0 : user.getFailCount()) + 1;
            user.setFailCount(fails);
            if (fails >= failLimit) {
                user.setLockUntil(LocalDateTime.now().plusMinutes(lockMinutes));
                user.setFailCount(0);
                logService.log("登录", user.getUsername(), "密码连续错误 " + failLimit + " 次，账号锁定", false);
            }
            userMapper.updateById(user);
            throw new BizException("用户名或密码错误" + (fails >= failLimit ? "，账号已锁定 " + lockMinutes + " 分钟" : "，还可尝试 " + (failLimit - fails) + " 次"));
        }

        user.setFailCount(0);
        user.setLockUntil(null);
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);
        userMapper.updateById(user);

        SysRole role = roleMapper.selectById(user.getRoleId());
        String roleCode = role == null ? "SAMPLER" : role.getCode();
        String scope = role == null ? "SELF" : role.getDataScope();
        UserContext.CurrentUser cu = new UserContext.CurrentUser(
                user.getId(), user.getUsername(), user.getRealName(), roleCode, user.getOrgId(), scope);
        UserContext.set(cu);

        logService.log("登录", "-", "登录成功");
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwtUtil.create(cu));
        data.put("user", userInfo(user));
        return data;
    }

    public Map<String, Object> info() {
        SysUser user = userMapper.selectById(UserContext.userId());
        if (user == null) {
            throw new BizException(401, "登录状态已失效");
        }
        return userInfo(user);
    }

    private Map<String, Object> userInfo(SysUser user) {
        SysRole role = roleMapper.selectById(user.getRoleId());
        SysOrg org = user.getOrgId() == null ? null : orgMapper.selectById(user.getOrgId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("realName", user.getRealName());
        m.put("avatar", user.getAvatar());
        m.put("phone", user.getPhone());
        m.put("email", user.getEmail());
        m.put("orgId", user.getOrgId());
        m.put("orgName", org == null ? "" : org.getName());
        m.put("roleCode", role == null ? "" : role.getCode());
        m.put("roleName", role == null ? "" : role.getName());
        m.put("dataScope", role == null ? "SELF" : role.getDataScope());
        m.put("lastLoginTime", user.getLastLoginTime());
        return m;
    }

    /** 按角色取菜单树（父分组 + 子菜单），只返回 visible=1 的项 */
    public List<Map<String, Object>> menus() {
        SysUser user = userMapper.selectById(UserContext.userId());
        List<Long> menuIds;
        if (user != null && user.getRoleId() != null && user.getRoleId() == 4L) {
            menuIds = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getVisible, 1))
                    .stream().map(SysMenu::getId).collect(Collectors.toList());
        } else {
            menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                            .eq(SysRoleMenu::getRoleId, user == null ? -1 : user.getRoleId()))
                    .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
        }
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(!menuIds.isEmpty(), SysMenu::getId, menuIds)
                .eq(SysMenu::getVisible, 1)
                .orderByAsc(SysMenu::getSort));
        // 把子菜单挂到分组下，前端侧边栏按分组渲染
        Map<Long, Map<String, Object>> groups = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();
        for (SysMenu m : all) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", m.getId());
            node.put("name", m.getName());
            node.put("path", m.getPath());
            node.put("icon", m.getIcon());
            node.put("children", new ArrayList<Map<String, Object>>());
            if (m.getParentId() != null && m.getParentId() == 0L) {
                groups.put(m.getId(), node);
                roots.add(node);
            }
        }
        for (SysMenu m : all) {
            if (m.getParentId() != null && m.getParentId() != 0L) {
                Map<String, Object> g = groups.get(m.getParentId());
                if (g != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> children = (List<Map<String, Object>>) g.get("children");
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", m.getId());
                    node.put("name", m.getName());
                    node.put("path", m.getPath());
                    node.put("icon", m.getIcon());
                    children.add(node);
                } else {
                    roots.add(new LinkedHashMap<>(Map.of("id", m.getId(), "name", m.getName(),
                            "path", m.getPath(), "icon", m.getIcon(), "children", new ArrayList<>())));
                }
            }
        }
        // 过滤掉没有子菜单的分组
        return roots.stream()
                .filter(r -> !((List<?>) r.get("children")).isEmpty() || r.get("path") != null)
                .collect(Collectors.toList());
    }

    public void updateProfile(ProfileDTO dto) {
        SysUser user = userMapper.selectById(UserContext.userId());
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        if (dto.getAvatar() != null && !dto.getAvatar().isBlank()) {
            user.setAvatar(dto.getAvatar());
        }
        userMapper.updateById(user);
        logService.log("修改个人资料", user.getUsername(), "更新姓名/联系方式/头像");
    }

    public void changePassword(PasswordDTO dto) {
        SysUser user = userMapper.selectById(UserContext.userId());
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException("当前密码不正确");
        }
        if (dto.getNewPassword() == null || dto.getNewPassword().length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        logService.log("修改密码", user.getUsername(), "密码已更新");
    }

    public List<SysMessage> messages() {
        return messageMapper.selectList(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getUserId, UserContext.userId())
                .orderByDesc(SysMessage::getCreateTime)
                .last("limit 20"));
    }

    public long unread() {
        return messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getUserId, UserContext.userId())
                .eq(SysMessage::getIsRead, 0));
    }

    public void readAll() {
        List<SysMessage> list = messageMapper.selectList(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getUserId, UserContext.userId())
                .eq(SysMessage::getIsRead, 0));
        for (SysMessage m : list) {
            m.setIsRead(1);
            messageMapper.updateById(m);
        }
    }
}
