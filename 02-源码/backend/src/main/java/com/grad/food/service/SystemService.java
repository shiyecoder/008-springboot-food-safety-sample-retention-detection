package com.grad.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.grad.food.common.BizException;
import com.grad.food.common.PageResult;
import com.grad.food.dto.UserSaveDTO;
import com.grad.food.entity.*;
import com.grad.food.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统管理：用户、角色、菜单分配、操作日志。
 * 注意：系统内不提供「菜单管理」入口，菜单由初始化脚本维护，避免管理员误改导致入口丢失。
 */
@Service
@RequiredArgsConstructor
public class SystemService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysOrgMapper orgMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysOperationLogMapper logMapper;
    private final PasswordEncoder passwordEncoder;
    private final LogService logService;

    private static final String DEFAULT_PWD = "123456";

    public PageResult<Map<String, Object>> users(long page, long size, String keyword, Long roleId, Long orgId, Integer status) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SysUser::getUsername, keyword).or().like(SysUser::getRealName, keyword));
        }
        if (roleId != null) {
            w.eq(SysUser::getRoleId, roleId);
        }
        if (orgId != null) {
            w.eq(SysUser::getOrgId, orgId);
        }
        if (status != null) {
            w.eq(SysUser::getStatus, status);
        }
        w.orderByAsc(SysUser::getId);
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), w);
        Map<Long, String> roleNames = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getName));
        Map<Long, String> orgNames = orgMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysOrg::getId, SysOrg::getName));
        Map<Long, String> scopes = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getDataScope));
        List<Map<String, Object>> rows = p.getRecords().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("avatar", u.getAvatar());
            m.put("orgId", u.getOrgId());
            m.put("orgName", orgNames.get(u.getOrgId()));
            m.put("roleId", u.getRoleId());
            m.put("roleName", roleNames.get(u.getRoleId()));
            m.put("dataScope", scopes.get(u.getRoleId()));
            m.put("phone", u.getPhone());
            m.put("email", u.getEmail());
            m.put("status", u.getStatus());
            m.put("locked", u.getLockUntil() != null && u.getLockUntil().isAfter(LocalDateTime.now()));
            m.put("lockUntil", u.getLockUntil());
            m.put("lastLoginTime", u.getLastLoginTime());
            m.put("remark", u.getRemark());
            m.put("createTime", u.getCreateTime());
            return m;   // password 字段一律不返回
        }).collect(Collectors.toList());
        return PageResult.of(p.getTotal(), page, size, rows);
    }

    @Transactional
    public void saveUser(UserSaveDTO dto) {
        if (dto.getId() == null) {
            if (dto.getUsername() == null || dto.getUsername().isBlank()) {
                throw new BizException("用户名必填");
            }
            long dup = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, dto.getUsername().trim()));
            if (dup > 0) {
                throw new BizException("用户名已存在");
            }
            SysUser u = new SysUser();
            u.setUsername(dto.getUsername().trim());
            u.setRealName(dto.getRealName());
            u.setPassword(passwordEncoder.encode(dto.getPassword() == null || dto.getPassword().isBlank()
                    ? DEFAULT_PWD : dto.getPassword()));
            u.setOrgId(dto.getOrgId());
            u.setRoleId(dto.getRoleId() == null ? 1L : dto.getRoleId());
            u.setPhone(dto.getPhone());
            u.setEmail(dto.getEmail());
            u.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
            u.setFailCount(0);
            u.setRemark(dto.getRemark());
            u.setVersion(0);
            u.setCreateTime(LocalDateTime.now());
            u.setUpdateTime(LocalDateTime.now());
            userMapper.insert(u);
            logService.log("新增用户", u.getUsername(), u.getRealName());
        } else {
            SysUser u = userMapper.selectById(dto.getId());
            if (u == null) {
                throw new BizException("用户不存在");
            }
            u.setRealName(dto.getRealName());
            u.setOrgId(dto.getOrgId());
            u.setRoleId(dto.getRoleId());
            u.setPhone(dto.getPhone());
            u.setEmail(dto.getEmail());
            u.setStatus(dto.getStatus());
            u.setRemark(dto.getRemark());
            if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
                u.setPassword(passwordEncoder.encode(dto.getPassword()));
            }
            u.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(u);
            logService.log("编辑用户", u.getUsername(), "更新用户信息");
        }
    }

    public void unlock(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException("用户不存在");
        }
        u.setLockUntil(null);
        u.setFailCount(0);
        u.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(u);
        logService.log("解锁账号", u.getUsername(), "手动解除登录锁定");
    }

    public void removeUser(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException("用户不存在");
        }
        if ("admin".equals(u.getUsername())) {
            throw new BizException("内置管理员不允许删除");
        }
        userMapper.deleteById(id);
        logService.log("删除用户", u.getUsername(), "逻辑删除", false);
    }

    public List<SysRole> roles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    @Transactional
    public void saveRole(Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        SysRole r = id == null ? new SysRole() : roleMapper.selectById(id);
        r.setCode(String.valueOf(body.get("code")));
        r.setName(String.valueOf(body.get("name")));
        r.setDataScope(body.get("dataScope") == null ? "SELF" : String.valueOf(body.get("dataScope")));
        r.setRemark(body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        r.setUpdateTime(LocalDateTime.now());
        if (id == null) {
            r.setCreateTime(LocalDateTime.now());
            roleMapper.insert(r);
        } else {
            roleMapper.updateById(r);
        }
        logService.log("保存角色", r.getName(), r.getDataScope());
    }

    public void removeRole(Long id) {
        long used = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleId, id));
        if (used > 0) {
            throw new BizException("该角色下还有 " + used + " 名用户，不能删除");
        }
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        logService.log("删除角色", String.valueOf(id), "逻辑删除", false);
    }

    /** 菜单树（父分组 + 子菜单），用于分配菜单。不提供菜单增删改。 */
    public List<Map<String, Object>> menuTree() {
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getVisible, 1).orderByAsc(SysMenu::getSort));
        List<Map<String, Object>> roots = new ArrayList<>();
        Map<Long, Map<String, Object>> idx = new LinkedHashMap<>();
        for (SysMenu m : all) {
            if (m.getParentId() != null && m.getParentId() == 0L) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", m.getId());
                node.put("name", m.getName());
                node.put("path", m.getPath());
                node.put("children", new ArrayList<Map<String, Object>>());
                idx.put(m.getId(), node);
                roots.add(node);
            }
        }
        for (SysMenu m : all) {
            if (m.getParentId() != null && m.getParentId() != 0L && idx.containsKey(m.getParentId())) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> c = (List<Map<String, Object>>) idx.get(m.getParentId()).get("children");
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", m.getId());
                node.put("name", m.getName());
                node.put("path", m.getPath());
                c.add(node);
            }
        }
        return roots;
    }

    public List<Long> roleMenus(Long roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    @Transactional
    public void assignMenus(Long roleId, List<Long> menuIds) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException("角色不存在");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            for (Long mid : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(mid);
                roleMenuMapper.insert(rm);
            }
        }
        logService.log("分配菜单", String.valueOf(roleId), "共 " + (menuIds == null ? 0 : menuIds.size()) + " 项");
    }

    public PageResult<SysOperationLog> logs(long page, long size, String keyword) {
        LambdaQueryWrapper<SysOperationLog> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SysOperationLog::getAction, keyword)
                    .or().like(SysOperationLog::getUserName, keyword)
                    .or().like(SysOperationLog::getTarget, keyword));
        }
        w.orderByDesc(SysOperationLog::getCreateTime);
        Page<SysOperationLog> p = logMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getTotal(), page, size, p.getRecords());
    }

    public List<SysOrg> orgs() {
        return orgMapper.selectList(new LambdaQueryWrapper<SysOrg>().orderByAsc(SysOrg::getSort));
    }
}
