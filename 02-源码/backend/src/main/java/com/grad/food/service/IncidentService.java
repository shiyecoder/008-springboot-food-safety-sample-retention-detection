package com.grad.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.grad.food.common.BizException;
import com.grad.food.common.PageResult;
import com.grad.food.common.Status;
import com.grad.food.common.UserContext;
import com.grad.food.entity.FoodIncident;
import com.grad.food.entity.FoodSample;
import com.grad.food.mapper.FoodIncidentMapper;
import com.grad.food.mapper.FoodSampleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 不合格事件服务。
 * 事件未关闭时关联留样保持锁定（locked=1），关闭后才解锁并允许进入销毁流程。
 */
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final FoodIncidentMapper incidentMapper;
    private final FoodSampleMapper sampleMapper;
    private final LogService logService;
    private final SampleService sampleService;

    public PageResult<Map<String, Object>> page(long page, long size, String status, String severity, String keyword) {
        LambdaQueryWrapper<FoodIncident> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            w.eq(FoodIncident::getStatus, status);
        }
        if (severity != null && !severity.isBlank()) {
            w.eq(FoodIncident::getSeverity, severity);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(FoodIncident::getIncidentNo, keyword)
                    .or().like(FoodIncident::getFoodName, keyword)
                    .or().like(FoodIncident::getSampleNo, keyword));
        }
        w.orderByDesc(FoodIncident::getOccurTime);
        Page<FoodIncident> p = incidentMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getTotal(), page, size,
                p.getRecords().stream().map(i -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", i.getId());
                    m.put("incidentNo", i.getIncidentNo());
                    m.put("sampleId", i.getSampleId());
                    m.put("sampleNo", i.getSampleNo());
                    m.put("foodName", i.getFoodName());
                    m.put("incidentType", i.getIncidentType());
                    m.put("severity", i.getSeverity());
                    m.put("severityLabel", severityLabel(i.getSeverity()));
                    m.put("status", i.getStatus());
                    m.put("statusLabel", statusLabel(i.getStatus()));
                    m.put("occurTime", i.getOccurTime());
                    m.put("discoverer", i.getDiscoverer());
                    m.put("sealLocation", i.getSealLocation());
                    m.put("stopSupply", i.getStopSupply());
                    m.put("traceBatch", i.getTraceBatch());
                    m.put("conclusion", i.getConclusion());
                    m.put("closeTime", i.getCloseTime());
                    return m;
                }).toList());
    }

    public Map<String, Object> detail(Long id) {
        FoodIncident i = incidentMapper.selectById(id);
        if (i == null) {
            throw new BizException("事件不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("incident", i);
        m.put("sample", sampleMapper.selectById(i.getSampleId()));
        return m;
    }

    /** 填写跟进结论（不关闭事件），原因不少于 5 个字 */
    @Transactional
    public void follow(Long id, String conclusion) {
        if (conclusion == null || conclusion.trim().length() < 5) {
            throw new BizException("跟进结论不少于 5 个字");
        }
        FoodIncident i = incidentMapper.selectById(id);
        if (i == null) {
            throw new BizException("事件不存在");
        }
        if (Status.INC_CLOSED.equals(i.getStatus())) {
            throw new BizException("事件已关闭，不可修改");
        }
        i.setConclusion(conclusion.trim());
        i.setStatus(Status.INC_FOLLOWING);
        i.setUpdateTime(LocalDateTime.now());
        incidentMapper.updateById(i);
        logService.log("填写事件跟进结论", i.getIncidentNo(), conclusion.trim());
    }

    /** 关闭事件并解锁留样；未填写结论不允许关闭 */
    @Transactional
    public void close(Long id, String conclusion) {
        FoodIncident i = incidentMapper.selectById(id);
        if (i == null) {
            throw new BizException("事件不存在");
        }
        if (Status.INC_CLOSED.equals(i.getStatus())) {
            throw new BizException("事件已关闭");
        }
        String text = (conclusion == null || conclusion.isBlank()) ? i.getConclusion() : conclusion.trim();
        if (text == null || text.length() < 5) {
            throw new BizException("关闭事件前必须填写跟进结论（不少于 5 个字）");
        }
        i.setConclusion(text);
        i.setStatus(Status.INC_CLOSED);
        i.setCloseTime(LocalDateTime.now());
        i.setUpdateTime(LocalDateTime.now());
        incidentMapper.updateById(i);

        FoodSample s = sampleMapper.selectById(i.getSampleId());
        if (s != null) {
            s.setLocked(0);
            s.setUpdateTime(LocalDateTime.now());
            sampleMapper.updateById(s);
            sampleService.writeRecord(s.getId(), "UNLOCK", s.getStatus(), s.getStatus(),
                    "事件 " + i.getIncidentNo() + " 已关闭，样品解锁");
            logService.notify(s.getSamplerId(), "不合格事件已关闭",
                    i.getFoodName() + "（" + i.getSampleNo() + "）已解锁，可进入销毁流程", "SAMPLE", s.getId());
        }
        logService.log("关闭不合格事件", i.getIncidentNo(), text);
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("handling", incidentMapper.selectCount(new LambdaQueryWrapper<FoodIncident>()
                .in(FoodIncident::getStatus, Status.INC_HANDLING, Status.INC_FOLLOWING)));
        m.put("closed", incidentMapper.selectCount(new LambdaQueryWrapper<FoodIncident>()
                .eq(FoodIncident::getStatus, Status.INC_CLOSED)));
        m.put("lockedSamples", sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>()
                .eq(FoodSample::getLocked, 1)));
        m.put("total", incidentMapper.selectCount(null));
        return m;
    }

    private String severityLabel(String s) {
        return switch (s == null ? "" : s) {
            case "GENERAL" -> "一般";
            case "SERIOUS" -> "严重";
            case "MAJOR" -> "重大";
            default -> s;
        };
    }

    private String statusLabel(String s) {
        return switch (s == null ? "" : s) {
            case "HANDLING" -> "处置中";
            case "FOLLOWING" -> "待跟进结论";
            case "CLOSED" -> "已关闭";
            default -> s;
        };
    }
}
