package com.grad.food.service;

import com.grad.food.common.UserContext;
import com.grad.food.entity.SysMessage;
import com.grad.food.entity.SysOperationLog;
import com.grad.food.mapper.SysMessageMapper;
import com.grad.food.mapper.SysOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作日志与站内消息。
 * 所有关键流转（复核、驳回、销毁审批、事件关闭）都会调用 log()，日志只追加。
 */
@Service
@RequiredArgsConstructor
public class LogService {

    private final SysOperationLogMapper logMapper;
    private final SysMessageMapper messageMapper;

    public void log(String action, String target, String detail, boolean success) {
        SysOperationLog l = new SysOperationLog();
        l.setUserId(UserContext.userId());
        l.setUserName(UserContext.realName());
        l.setAction(action);
        l.setTarget(target);
        l.setDetail(detail);
        l.setResult(success ? "SUCCESS" : "FAIL");
        l.setIp(currentIp());
        l.setCreateTime(LocalDateTime.now());
        logMapper.insert(l);
    }

    public void log(String action, String target, String detail) {
        log(action, target, detail, true);
    }

    /** 推送站内消息，bizType+bizId 让前端可以跳到对应详情 */
    public void notify(Long userId, String title, String content, String bizType, Long bizId) {
        if (userId == null) {
            return;
        }
        SysMessage m = new SysMessage();
        m.setUserId(userId);
        m.setTitle(title);
        m.setContent(content);
        m.setBizType(bizType);
        m.setBizId(bizId);
        m.setIsRead(0);
        m.setCreateTime(LocalDateTime.now());
        messageMapper.insert(m);
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest req = attrs.getRequest();
            String ip = req.getHeader("X-Forwarded-For");
            return ip == null ? req.getRemoteAddr() : ip.split(",")[0];
        } catch (Exception e) {
            return null;
        }
    }
}
