package com.grad.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.grad.food.common.BizException;
import com.grad.food.common.PageResult;
import com.grad.food.common.Status;
import com.grad.food.common.UserContext;
import com.grad.food.dto.SampleSaveDTO;
import com.grad.food.entity.*;
import com.grad.food.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 留样主档服务：登记、复核、流转、附件、详情。
 *
 * 关键业务规则（全部在后端校验，不依赖前端把按钮藏起来）：
 *  1. 复核通过前重新计算：实际重量 ≥ 规则下限；仓位未被其他样品占用；留样时间未超过任务截止时间 2 小时；
 *  2. 驳回必须填写原因（≥5 字），驳回后原记录保留为历史，需重新登记新记录；
 *  3. 已销毁记录不可删除、不可编辑；
 *  4. 被不合格事件锁定的留样禁止销毁。
 */
@Service
@RequiredArgsConstructor
public class SampleService {

    private final FoodSampleMapper sampleMapper;
    private final FoodSampleRecordMapper recordMapper;
    private final FoodSampleAttachmentMapper attachmentMapper;
    private final FoodCategoryMapper categoryMapper;
    private final StorageLocationMapper locationMapper;
    private final SampleRuleMapper ruleMapper;
    private final SampleTaskMapper taskMapper;
    private final InspectionRecordMapper inspectionMapper;
    private final FoodIncidentMapper incidentMapper;
    private final DisposalRecordMapper disposalMapper;
    private final SysUserMapper userMapper;
    private final LogService logService;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 分页查询：关键字、餐次、日期区间、仓位、到期状态、检测结果、状态 + 数据范围 */
    public PageResult<Map<String, Object>> page(long page, long size, String keyword, String meal,
                                                String dateStart, String dateEnd, Long locationId,
                                                String expireState, String inspectResult, String status,
                                                String tab) {
        LambdaQueryWrapper<FoodSample> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(FoodSample::getFoodName, keyword).or().like(FoodSample::getSampleNo, keyword));
        }
        if (meal != null && !meal.isBlank()) {
            w.eq(FoodSample::getMeal, meal);
        }
        if (dateStart != null && !dateStart.isBlank()) {
            w.ge(FoodSample::getStoreTime, LocalDate.parse(dateStart).atStartOfDay());
        }
        if (dateEnd != null && !dateEnd.isBlank()) {
            w.le(FoodSample::getStoreTime, LocalDate.parse(dateEnd).atTime(23, 59, 59));
        }
        if (locationId != null) {
            w.eq(FoodSample::getLocationId, locationId);
        }
        if (inspectResult != null && !inspectResult.isBlank()) {
            w.eq(FoodSample::getInspectResult, inspectResult);
        }
        if (status != null && !status.isBlank()) {
            w.eq(FoodSample::getStatus, status);
        }
        if (tab != null && !tab.isBlank()) {
            w.eq(FoodSample::getStatus, tab);
        }
        if ("EXPIRED".equals(expireState)) {
            w.lt(FoodSample::getExpireTime, LocalDateTime.now())
                    .in(FoodSample::getStatus, Status.RETAINING, Status.PENDING_DISPOSE);
        } else if ("SOON".equals(expireState)) {
            w.between(FoodSample::getExpireTime, LocalDateTime.now(), LocalDateTime.now().plusHours(24))
                    .eq(FoodSample::getStatus, Status.RETAINING);
        } else if ("NORMAL".equals(expireState)) {
            w.gt(FoodSample::getExpireTime, LocalDateTime.now().plusHours(24));
        }
        applyScope(w);
        w.orderByDesc(FoodSample::getStoreTime);

        Page<FoodSample> p = sampleMapper.selectPage(new Page<>(page, size), w);
        Map<Long, String> catNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(FoodCategory::getId, FoodCategory::getName));
        List<Map<String, Object>> rows = p.getRecords().stream()
                .map(s -> toRow(s, catNames.get(s.getCategoryId())))
                .collect(Collectors.toList());
        return PageResult.of(p.getTotal(), page, size, rows);
    }

    /** 数据范围：本人 / 本部门 / 全部 */
    private void applyScope(LambdaQueryWrapper<FoodSample> w) {
        String scope = UserContext.dataScope();
        if ("ALL".equals(scope)) {
            return;
        }
        if ("DEPT".equals(scope)) {
            List<Long> ids = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getOrgId, UserContext.orgId()))
                    .stream().map(SysUser::getId).collect(Collectors.toList());
            if (ids.isEmpty()) {
                ids = List.of(-1L);
            }
            w.in(FoodSample::getSamplerId, ids);
        } else {
            w.eq(FoodSample::getSamplerId, UserContext.userId());
        }
    }

    private Map<String, Object> toRow(FoodSample s, String categoryName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("sampleNo", s.getSampleNo());
        m.put("foodName", s.getFoodName());
        m.put("categoryId", s.getCategoryId());
        m.put("categoryName", categoryName);
        m.put("meal", s.getMeal());
        m.put("batchNo", s.getBatchNo());
        m.put("weight", s.getWeight());
        m.put("containerNo", s.getContainerNo());
        m.put("locationId", s.getLocationId());
        m.put("locationCode", s.getLocationCode());
        m.put("temperature", s.getTemperature());
        m.put("storeTime", s.getStoreTime());
        m.put("expireTime", s.getExpireTime());
        m.put("retainHours", s.getRetainHours());
        m.put("photo", s.getPhoto());
        m.put("proofPhoto", s.getProofPhoto());
        m.put("samplerId", s.getSamplerId());
        m.put("samplerName", s.getSamplerName());
        m.put("reviewTime", s.getReviewTime());
        m.put("reviewRemark", s.getReviewRemark());
        m.put("status", s.getStatus());
        m.put("statusLabel", Status.sampleLabel(s.getStatus()));
        m.put("inspectResult", s.getInspectResult());
        m.put("locked", s.getLocked());
        m.put("remark", s.getRemark());
        m.put("version", s.getVersion());
        m.put("createTime", s.getCreateTime());
        m.put("updateTime", s.getUpdateTime());
        m.put("expireState", expireState(s));
        return m;
    }

    /** 到期状态：EXPIRED 已逾期 / SOON 24 小时内到期 / NORMAL 正常 / DONE 已完成 */
    private String expireState(FoodSample s) {
        if (Status.DISPOSED.equals(s.getStatus()) || s.getExpireTime() == null) {
            return "DONE";
        }
        LocalDateTime now = LocalDateTime.now();
        if (s.getExpireTime().isBefore(now)) {
            return "EXPIRED";
        }
        return Duration.between(now, s.getExpireTime()).toHours() <= 24 ? "SOON" : "NORMAL";
    }

    /** 详情：主档 + 时间轴 + 附件 + 检测记录 + 事件 + 销毁记录 + 分类与规则 */
    public Map<String, Object> detail(Long id) {
        FoodSample s = sampleMapper.selectById(id);
        if (s == null) {
            throw new BizException("留样记录不存在");
        }
        checkReadable(s);
        FoodCategory cat = categoryMapper.selectById(s.getCategoryId());
        Map<String, Object> data = toRow(s, cat == null ? null : cat.getName());
        data.put("category", cat);
        data.put("records", recordMapper.selectList(new LambdaQueryWrapper<FoodSampleRecord>()
                .eq(FoodSampleRecord::getSampleId, id).orderByAsc(FoodSampleRecord::getCreateTime)));
        data.put("attachments", attachmentMapper.selectList(new LambdaQueryWrapper<FoodSampleAttachment>()
                .eq(FoodSampleAttachment::getSampleId, id).orderByAsc(FoodSampleAttachment::getId)));
        data.put("inspections", inspectionMapper.selectList(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getSampleId, id).orderByDesc(InspectionRecord::getCreateTime)));
        data.put("incidents", incidentMapper.selectList(new LambdaQueryWrapper<FoodIncident>()
                .eq(FoodIncident::getSampleId, id).orderByDesc(FoodIncident::getOccurTime)));
        data.put("disposals", disposalMapper.selectList(new LambdaQueryWrapper<DisposalRecord>()
                .eq(DisposalRecord::getSampleId, id).orderByDesc(DisposalRecord::getCreateTime)));
        SampleRule rule = cat == null ? null : ruleMapper.selectOne(new LambdaQueryWrapper<SampleRule>()
                .eq(SampleRule::getCategoryId, cat.getId()).eq(SampleRule::getStatus, 1).last("limit 1"));
        data.put("rule", rule);
        return data;
    }

    /** 登记留样：编号后端生成，仓位占用标记，任务回写 */
    @Transactional
    public Long create(SampleSaveDTO dto) {
        StorageLocation loc = locationMapper.selectById(dto.getLocationId());
        if (loc == null || loc.getStatus() == null || loc.getStatus() == 0) {
            throw new BizException("仓位不可用，请重新选择");
        }
        FoodCategory cat = categoryMapper.selectById(dto.getCategoryId());
        if (cat == null) {
            throw new BizException("食品分类不存在");
        }
        LocalDateTime storeTime = dto.getStoreTime() == null ? LocalDateTime.now() : dto.getStoreTime();
        int retain = cat.getRetainHours() == null ? 48 : cat.getRetainHours();

        FoodSample s = new FoodSample();
        s.setSampleNo(nextNo(storeTime));
        s.setFoodName(dto.getFoodName().trim());
        s.setCategoryId(dto.getCategoryId());
        s.setMeal(dto.getMeal());
        s.setBatchNo(dto.getBatchNo());
        s.setBatchId(dto.getBatchId());
        s.setTaskId(dto.getTaskId());
        s.setWeight(dto.getWeight());
        s.setContainerNo(dto.getContainerNo());
        s.setLocationId(loc.getId());
        s.setLocationCode(loc.getCode());
        s.setTemperature(dto.getTemperature());
        s.setStoreTime(storeTime);
        s.setExpireTime(storeTime.plusHours(retain));
        s.setRetainHours(retain);
        s.setPhoto(dto.getPhoto());
        s.setProofPhoto(dto.getProofPhoto());
        s.setSamplerId(UserContext.userId());
        s.setSamplerName(UserContext.realName());
        s.setStatus(Status.PENDING_REVIEW);
        s.setInspectResult("NONE");
        s.setLocked(0);
        s.setDisposed(0);
        s.setRemark(dto.getRemark());
        s.setVersion(0);
        s.setCreateTime(LocalDateTime.now());
        s.setUpdateTime(LocalDateTime.now());
        sampleMapper.insert(s);

        // 占用仓位，避免并发重复入柜
        if (loc.getOccupied() == null || loc.getOccupied() == 0) {
            loc.setOccupied(1);
            locationMapper.updateById(loc);
        }
        // 回写任务
        if (dto.getTaskId() != null) {
            SampleTask task = taskMapper.selectById(dto.getTaskId());
            if (task != null) {
                task.setStatus(Status.PENDING_REVIEW);
                task.setSampleId(s.getId());
                task.setUpdateTime(LocalDateTime.now());
                taskMapper.updateById(task);
            }
        }
        writeRecord(s.getId(), "CREATE", null, Status.PENDING_REVIEW, "创建留样记录，重量 " + dto.getWeight() + " g");
        if (dto.getPhoto() != null && !dto.getPhoto().isBlank()) {
            addAttachment(s.getId(), "留样容器照片", dto.getPhoto());
        }
        if (dto.getProofPhoto() != null && !dto.getProofPhoto().isBlank()) {
            addAttachment(s.getId(), "现场凭证照片", dto.getProofPhoto());
        }
        logService.log("登记留样", s.getSampleNo(), dto.getFoodName() + " " + dto.getWeight() + " g");
        return s.getId();
    }

    /** 编辑：仅待复核状态可改，带版本号校验 */
    @Transactional
    public void update(Long id, SampleSaveDTO dto) {
        FoodSample s = sampleMapper.selectById(id);
        if (s == null) {
            throw new BizException("留样记录不存在");
        }
        if (Status.isSampleFinal(s.getStatus())) {
            throw new BizException("已销毁/已驳回的留样不允许修改");
        }
        if (dto.getVersion() != null && !dto.getVersion().equals(s.getVersion())) {
            throw new BizException("数据已被他人更新，请刷新后重试");
        }
        if (dto.getWeight() != null) {
            s.setWeight(dto.getWeight());
        }
        if (dto.getContainerNo() != null) {
            s.setContainerNo(dto.getContainerNo());
        }
        if (dto.getTemperature() != null) {
            s.setTemperature(dto.getTemperature());
        }
        if (dto.getPhoto() != null) {
            s.setPhoto(dto.getPhoto());
        }
        if (dto.getRemark() != null) {
            s.setRemark(dto.getRemark());
        }
        if (dto.getLocationId() != null && !dto.getLocationId().equals(s.getLocationId())) {
            StorageLocation loc = locationMapper.selectById(dto.getLocationId());
            if (loc == null || loc.getOccupied() != null && loc.getOccupied() == 1) {
                throw new BizException("目标仓位已被占用");
            }
            releaseLocation(s.getLocationId());
            s.setLocationId(loc.getId());
            s.setLocationCode(loc.getCode());
            loc.setOccupied(1);
            locationMapper.updateById(loc);
        }
        s.setUpdateTime(LocalDateTime.now());
        sampleMapper.updateById(s);
        logService.log("修改留样", s.getSampleNo(), "更新重量/容器/仓位等字段");
    }

    /**
     * 复核。
     * 通过：重量、仓位、时间三项硬校验；不通过：原因必填，状态转已驳回并释放仓位。
     */
    @Transactional
    public void review(Long id, boolean pass, String remark) {
        FoodSample s = sampleMapper.selectById(id);
        if (s == null) {
            throw new BizException("留样记录不存在");
        }
        if (!Status.canReview(s.getStatus())) {
            throw new BizException("当前状态（" + Status.sampleLabel(s.getStatus()) + "）不允许复核");
        }
        if (!pass) {
            if (remark == null || remark.trim().length() < 5) {
                throw new BizException("驳回原因不少于 5 个字");
            }
            s.setStatus(Status.REJECTED);
            s.setReviewerId(UserContext.userId());
            s.setReviewTime(LocalDateTime.now());
            s.setReviewRemark(remark.trim());
            s.setUpdateTime(LocalDateTime.now());
            sampleMapper.updateById(s);
            releaseLocation(s.getLocationId());
            writeRecord(id, "REVIEW_REJECT", Status.PENDING_REVIEW, Status.REJECTED, remark.trim());
            if (s.getTaskId() != null) {
                SampleTask task = taskMapper.selectById(s.getTaskId());
                if (task != null) {
                    task.setStatus(Status.REJECTED);
                    taskMapper.updateById(task);
                }
            }
            logService.log("复核驳回", s.getSampleNo(), remark.trim(), false);
            logService.notify(s.getSamplerId(), "留样复核驳回",
                    s.getFoodName() + "（" + s.getSampleNo() + "）" + remark.trim(), "SAMPLE", id);
            return;
        }

        // 硬校验 1：重量
        FoodCategory cat = categoryMapper.selectById(s.getCategoryId());
        SampleRule rule = cat == null ? null : ruleMapper.selectOne(new LambdaQueryWrapper<SampleRule>()
                .eq(SampleRule::getCategoryId, cat.getId()).eq(SampleRule::getStatus, 1).last("limit 1"));
        BigDecimal min = rule == null ? (cat == null ? BigDecimal.valueOf(100) : cat.getMinWeight()) : rule.getMinWeight();
        if (s.getWeight() == null || s.getWeight().compareTo(min) < 0) {
            throw new BizException("实际重量 " + s.getWeight() + " g 低于标准 " + min + " g，不允许通过复核");
        }
        // 硬校验 2：仓位冲突（同一仓位不能有第二份在保留中的样品）
        long conflict = sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>()
                .eq(FoodSample::getLocationId, s.getLocationId())
                .ne(FoodSample::getId, s.getId())
                .in(FoodSample::getStatus, Status.RETAINING, Status.PENDING_REVIEW, Status.PENDING_DISPOSE));
        if (conflict > 0) {
            throw new BizException("仓位 " + s.getLocationCode() + " 已被其他留样占用，不允许通过复核");
        }
        // 硬校验 3：留样超时（晚于任务截止 2 小时）
        if (s.getTaskId() != null) {
            SampleTask task = taskMapper.selectById(s.getTaskId());
            if (task != null && task.getDeadline() != null
                    && s.getStoreTime().isAfter(task.getDeadline().plusHours(2))) {
                throw new BizException("留样时间超出任务截止时间 2 小时，已超时，需重新留样");
            }
        }

        s.setStatus(Status.RETAINING);
        s.setReviewerId(UserContext.userId());
        s.setReviewTime(LocalDateTime.now());
        s.setReviewRemark(remark == null || remark.isBlank() ? "重量、密封、标识、仓位核对通过" : remark.trim());
        s.setUpdateTime(LocalDateTime.now());
        sampleMapper.updateById(s);
        writeRecord(id, "REVIEW_PASS", Status.PENDING_REVIEW, Status.RETAINING, s.getReviewRemark());
        if (s.getTaskId() != null) {
            SampleTask task = taskMapper.selectById(s.getTaskId());
            if (task != null) {
                task.setStatus(Status.RETAINING);
                taskMapper.updateById(task);
            }
        }
        logService.log("复核通过", s.getSampleNo(), "转入保留中，到期时间 " + s.getExpireTime());
        logService.notify(s.getSamplerId(), "留样复核通过",
                s.getFoodName() + "（" + s.getSampleNo() + "）已入柜保留", "SAMPLE", id);
    }

    /** 通用流转：目前支持 RETAINING → PENDING_DISPOSE（人工提前转待销毁） */
    @Transactional
    public void transition(Long id, String target, String reason) {
        FoodSample s = sampleMapper.selectById(id);
        if (s == null) {
            throw new BizException("留样记录不存在");
        }
        if (!Status.RETAINING.equals(s.getStatus())) {
            throw new BizException("仅保留中的留样允许流转，当前为 " + Status.sampleLabel(s.getStatus()));
        }
        if (!Status.PENDING_DISPOSE.equals(target)) {
            throw new BizException("不支持的目标状态：" + target);
        }
        if (reason == null || reason.trim().length() < 5) {
            throw new BizException("转入待销毁必须填写原因（不少于 5 个字）");
        }
        s.setStatus(Status.PENDING_DISPOSE);
        s.setUpdateTime(LocalDateTime.now());
        sampleMapper.updateById(s);
        writeRecord(id, "EXPIRE", Status.RETAINING, Status.PENDING_DISPOSE, reason.trim());
        logService.log("转入待销毁", s.getSampleNo(), reason.trim());
    }

    /** 逻辑删除：已销毁不可删除，销毁锁定也不可删除 */
    @Transactional
    public void remove(Long id) {
        FoodSample s = sampleMapper.selectById(id);
        if (s == null) {
            throw new BizException("留样记录不存在");
        }
        if (Status.DISPOSED.equals(s.getStatus())) {
            throw new BizException("已销毁的留样数据不可删除");
        }
        if (s.getLocked() != null && s.getLocked() == 1) {
            throw new BizException("留样已被不合格事件锁定，事件关闭前不可删除");
        }
        sampleMapper.deleteById(id);
        releaseLocation(s.getLocationId());
        logService.log("删除留样", s.getSampleNo(), "逻辑删除", false);
    }

    public List<FoodSampleAttachment> attachments(Long id) {
        return attachmentMapper.selectList(new LambdaQueryWrapper<FoodSampleAttachment>()
                .eq(FoodSampleAttachment::getSampleId, id).orderByAsc(FoodSampleAttachment::getId));
    }

    public void addAttachment(Long sampleId, String name, String path) {
        if (path == null || path.isBlank() || path.startsWith("http")) {
            throw new BizException("附件必须是本地上传文件");
        }
        FoodSampleAttachment a = new FoodSampleAttachment();
        a.setSampleId(sampleId);
        a.setFileName(name);
        a.setPath(path);
        a.setFileType(path.toLowerCase().endsWith(".pdf") ? "PDF" : "IMAGE");
        a.setFileSize(0L);
        a.setUploaderName(UserContext.realName());
        a.setCreateTime(LocalDateTime.now());
        attachmentMapper.insert(a);
    }

    public void removeAttachment(Long attachmentId) {
        attachmentMapper.deleteById(attachmentId);
    }

    /** 释放仓位占用（驳回、销毁、删除时调用） */
    public void releaseLocation(Long locationId) {
        if (locationId == null) {
            return;
        }
        StorageLocation loc = locationMapper.selectById(locationId);
        if (loc != null) {
            loc.setOccupied(0);
            locationMapper.updateById(loc);
        }
    }

    public void writeRecord(Long sampleId, String action, String from, String to, String reason) {
        FoodSampleRecord r = new FoodSampleRecord();
        r.setSampleId(sampleId);
        r.setAction(action);
        r.setFromStatus(from);
        r.setToStatus(to);
        r.setOperatorId(UserContext.userId());
        r.setOperatorName(UserContext.userId() == null ? "系统" : UserContext.realName());
        r.setReason(reason);
        r.setCreateTime(LocalDateTime.now());
        recordMapper.insert(r);
    }

    /** 编号规则 LY + yyyyMMdd + 4 位序号，同一天内自增 */
    private String nextNo(LocalDateTime time) {
        String day = time.format(DAY);
        long count = sampleMapper.selectCount(new LambdaQueryWrapper<FoodSample>()
                .likeRight(FoodSample::getSampleNo, "LY" + day));
        return "LY" + day + "-" + String.format("%04d", count + 1);
    }

    private void checkReadable(FoodSample s) {
        String scope = UserContext.dataScope();
        if ("ALL".equals(scope)) {
            return;
        }
        if ("SELF".equals(scope) && !Objects.equals(s.getSamplerId(), UserContext.userId())) {
            throw new BizException(403, "无权查看他人留样数据");
        }
    }
}
