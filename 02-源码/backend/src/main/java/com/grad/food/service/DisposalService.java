package com.grad.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.grad.food.common.BizException;
import com.grad.food.common.PageResult;
import com.grad.food.common.Status;
import com.grad.food.common.UserContext;
import com.grad.food.dto.DisposeDTO;
import com.grad.food.entity.*;
import com.grad.food.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 到期与销毁服务。
 *
 * 规则：
 *  1. 只有「待销毁」且未被事件锁定的留样可以发起销毁；
 *  2. 双人监督：执行人与监督人不得为同一人，且必须有照片；
 *  3. 主管审批通过后：留样置「已销毁」、仓位释放、原始数据锁定（不可修改/删除）。
 */
@Service
@RequiredArgsConstructor
public class DisposalService {

    private final DisposalRecordMapper disposalMapper;
    private final FoodSampleMapper sampleMapper;
    private final StorageLocationMapper locationMapper;
    private final FoodIncidentMapper incidentMapper;
    private final LogService logService;
    private final SampleService sampleService;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 待销毁清单（默认按逾期天数倒序），支持只看逾期/锁定 */
    public PageResult<Map<String, Object>> pending(long page, long size, String tab, String keyword) {
        LambdaQueryWrapper<FoodSample> w = new LambdaQueryWrapper<>();
        if ("EXPIRED".equals(tab)) {
            w.eq(FoodSample::getStatus, Status.PENDING_DISPOSE)
                    .lt(FoodSample::getExpireTime, LocalDateTime.now());
        } else if ("LOCKED".equals(tab)) {
            w.eq(FoodSample::getLocked, 1)
                    .in(FoodSample::getStatus, Status.RETAINING, Status.PENDING_DISPOSE);
        } else if ("DISPOSED".equals(tab)) {
            w.eq(FoodSample::getStatus, Status.DISPOSED);
        } else {
            w.eq(FoodSample::getStatus, Status.PENDING_DISPOSE);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(FoodSample::getSampleNo, keyword).or().like(FoodSample::getFoodName, keyword));
        }
        w.orderByAsc(FoodSample::getExpireTime);
        Page<FoodSample> p = sampleMapper.selectPage(new Page<>(page, size), w);

        Map<Long, DisposalRecord> latest = new HashMap<>();
        List<Long> ids = p.getRecords().stream().map(FoodSample::getId).collect(Collectors.toList());
        if (!ids.isEmpty()) {
            disposalMapper.selectList(new LambdaQueryWrapper<DisposalRecord>()
                            .in(DisposalRecord::getSampleId, ids).orderByDesc(DisposalRecord::getCreateTime))
                    .forEach(d -> latest.putIfAbsent(d.getSampleId(), d));
        }

        List<Map<String, Object>> rows = p.getRecords().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("sampleNo", s.getSampleNo());
            m.put("foodName", s.getFoodName());
            m.put("locationCode", s.getLocationCode());
            m.put("weight", s.getWeight());
            m.put("expireTime", s.getExpireTime());
            m.put("storeTime", s.getStoreTime());
            m.put("status", s.getStatus());
            m.put("statusLabel", Status.sampleLabel(s.getStatus()));
            m.put("locked", s.getLocked());
            m.put("photo", s.getPhoto());
            long overdueHours = s.getExpireTime() == null ? 0
                    : Math.max(0, java.time.Duration.between(s.getExpireTime(), LocalDateTime.now()).toHours());
            m.put("overdueHours", overdueHours);
            m.put("overdueDays", Math.round(overdueHours / 24.0 * 10) / 10.0);
            DisposalRecord d = latest.get(s.getId());
            m.put("disposalStatus", d == null ? null : d.getStatus());
            m.put("disposalPhoto", d == null ? null : d.getPhoto());
            m.put("canDispose", Status.canDispose(s.getStatus(), s.getLocked()));
            return m;
        }).collect(Collectors.toList());
        return PageResult.of(p.getTotal(), page, size, rows);
    }

    /** 发起销毁（登记双人信息），状态先置为待审批 */
    @Transactional
    public Long create(DisposeDTO dto) {
        FoodSample s = sampleMapper.selectById(dto.getSampleId());
        if (s == null) {
            throw new BizException("留样记录不存在");
        }
        if (!Status.PENDING_DISPOSE.equals(s.getStatus())) {
            throw new BizException("当前状态（" + Status.sampleLabel(s.getStatus()) + "）不允许销毁");
        }
        if (s.getLocked() != null && s.getLocked() == 1) {
            throw new BizException("该留样被不合格事件锁定，事件关闭前不允许销毁");
        }
        if (dto.getExecutor().trim().equals(dto.getSupervisor().trim())) {
            throw new BizException("双人监督要求执行人与监督人不是同一人");
        }
        if (dto.getPhoto() == null || dto.getPhoto().isBlank() || dto.getPhoto().startsWith("http")) {
            throw new BizException("请上传销毁现场照片（本地文件）");
        }
        DisposalRecord r = new DisposalRecord();
        r.setDisposalNo("XH" + LocalDate.now().format(DAY) + "-" +
                String.format("%03d", disposalMapper.selectCount(new LambdaQueryWrapper<DisposalRecord>()
                        .ge(DisposalRecord::getCreateTime, LocalDate.now().atStartOfDay())) + 1));
        r.setSampleId(s.getId());
        r.setSampleNo(s.getSampleNo());
        r.setFoodName(s.getFoodName());
        r.setMethod(dto.getMethod());
        r.setDisposeTime(dto.getDisposeTime() == null ? LocalDateTime.now() : dto.getDisposeTime());
        r.setExecutor(dto.getExecutor().trim());
        r.setSupervisor(dto.getSupervisor().trim());
        r.setWeight(dto.getWeight());
        r.setPhoto(dto.getPhoto());
        r.setStatus("PENDING_APPROVE");
        r.setRemark(dto.getRemark());
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        disposalMapper.insert(r);
        logService.log("登记销毁申请", r.getDisposalNo(), s.getFoodName() + " " + dto.getWeight() + " g");
        logService.notify(7L, "销毁申请待审批", s.getFoodName() + "（" + s.getSampleNo() + "）已提交销毁申请", "DISPOSAL", r.getId());
        return r.getId();
    }

    /** 主管审批：通过后留样置已销毁、释放仓位、锁定原始数据 */
    @Transactional
    public void approve(Long id, boolean pass, String remark) {
        DisposalRecord r = disposalMapper.selectById(id);
        if (r == null) {
            throw new BizException("销毁记录不存在");
        }
        if (!"PENDING_APPROVE".equals(r.getStatus())) {
            throw new BizException("该销毁记录已审批，请勿重复操作");
        }
        if (!pass) {
            if (remark == null || remark.trim().length() < 5) {
                throw new BizException("驳回原因不少于 5 个字");
            }
            r.setStatus("REJECTED");
            r.setRemark(remark.trim());
            r.setUpdateTime(LocalDateTime.now());
            disposalMapper.updateById(r);
            logService.log("驳回销毁申请", r.getDisposalNo(), remark.trim(), false);
            return;
        }
        FoodSample s = sampleMapper.selectById(r.getSampleId());
        if (s != null) {
            if (s.getLocked() != null && s.getLocked() == 1) {
                throw new BizException("留样仍被事件锁定，无法完成销毁审批");
            }
            s.setStatus(Status.DISPOSED);
            s.setDisposed(1);
            s.setUpdateTime(LocalDateTime.now());
            sampleMapper.updateById(s);
            sampleService.writeRecord(s.getId(), "DISPOSE", Status.PENDING_DISPOSE, Status.DISPOSED,
                    "双人监督销毁：" + r.getExecutor() + " / 监督 " + r.getSupervisor());
            sampleService.releaseLocation(s.getLocationId());
        }
        r.setStatus("APPROVED");
        r.setApproveBy(UserContext.realName());
        r.setApproveTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        disposalMapper.updateById(r);
        logService.log("审批销毁", r.getDisposalNo(), "审批通过，原始数据锁定");
    }

    public PageResult<Map<String, Object>> records(long page, long size, String status) {
        LambdaQueryWrapper<DisposalRecord> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            w.eq(DisposalRecord::getStatus, status);
        }
        w.orderByDesc(DisposalRecord::getCreateTime);
        Page<DisposalRecord> p = disposalMapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getTotal(), page, size, p.getRecords().stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("disposalNo", d.getDisposalNo());
            m.put("sampleNo", d.getSampleNo());
            m.put("foodName", d.getFoodName());
            m.put("method", d.getMethod());
            m.put("disposeTime", d.getDisposeTime());
            m.put("executor", d.getExecutor());
            m.put("supervisor", d.getSupervisor());
            m.put("weight", d.getWeight());
            m.put("photo", d.getPhoto());
            m.put("status", d.getStatus());
            m.put("approveBy", d.getApproveBy());
            m.put("approveTime", d.getApproveTime());
            m.put("remark", d.getRemark());
            return m;
        }).toList());
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingDispose", sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>()
                .eq(FoodSample::getStatus, Status.PENDING_DISPOSE)));
        m.put("expired", sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>()
                .eq(FoodSample::getStatus, Status.PENDING_DISPOSE)
                .lt(FoodSample::getExpireTime, LocalDateTime.now())));
        m.put("locked", sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>()
                .eq(FoodSample::getLocked, 1)));
        m.put("disposed", sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>()
                .eq(FoodSample::getStatus, Status.DISPOSED)));
        m.put("pendingApprove", disposalMapper.selectCount(new LambdaQueryWrapper<DisposalRecord>()
                .eq(DisposalRecord::getStatus, "PENDING_APPROVE")));
        return m;
    }
}
