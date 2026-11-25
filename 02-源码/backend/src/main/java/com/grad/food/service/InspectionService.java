package com.grad.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.grad.food.common.BizException;
import com.grad.food.common.PageResult;
import com.grad.food.common.Status;
import com.grad.food.common.UserContext;
import com.grad.food.dto.InspectSaveDTO;
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
 * 检测服务。
 *
 * 状态机：待检测(PENDING) → 检测中(TESTING) → 待复核(PENDING_REVIEW) → 已合格(QUALIFIED)/已不合格(UNQUALIFIED)。
 * 规则：
 *  1. 提交「不合格」自动生成食品安全事件，并锁定留样（locked=1），事件关闭前禁止销毁；
 *  2. 「待复检」不改变留样结论，需新增检测记录重新检测；
 *  3. 复核通过后回写留样的 inspect_result。
 */
@Service
@RequiredArgsConstructor
public class InspectionService {

    private final InspectionRecordMapper inspectMapper;
    private final InspectionItemMapper itemMapper;
    private final FoodSampleMapper sampleMapper;
    private final FoodIncidentMapper incidentMapper;
    private final LogService logService;
    private final SampleService sampleService;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    public PageResult<Map<String, Object>> page(long page, long size, String keyword, String type,
                                                String result, String status, String tab,
                                                String dateStart, String dateEnd) {
        LambdaQueryWrapper<InspectionRecord> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(InspectionRecord::getInspectNo, keyword)
                    .or().like(InspectionRecord::getSampleNo, keyword)
                    .or().like(InspectionRecord::getFoodName, keyword));
        }
        if (type != null && !type.isBlank()) {
            w.eq(InspectionRecord::getInspectType, type);
        }
        if (result != null && !result.isBlank()) {
            w.eq(InspectionRecord::getResult, result);
        }
        if (status != null && !status.isBlank()) {
            w.eq(InspectionRecord::getStatus, status);
        }
        if (tab != null && !tab.isBlank()) {
            switch (tab) {
                case "TODO" -> w.in(InspectionRecord::getStatus, Status.INS_PENDING, Status.INS_TESTING);
                case "DONE" -> w.in(InspectionRecord::getStatus, Status.INS_PASSED, Status.INS_FAILED);
                case "ABNORMAL" -> w.eq(InspectionRecord::getStatus, Status.INS_FAILED);
                case "PROCESSING" -> w.eq(InspectionRecord::getStatus, Status.INS_TESTING);
                default -> w.eq(InspectionRecord::getStatus, tab);
            }
        }
        if (dateStart != null && !dateStart.isBlank()) {
            w.ge(InspectionRecord::getCreateTime, LocalDate.parse(dateStart).atStartOfDay());
        }
        if (dateEnd != null && !dateEnd.isBlank()) {
            w.le(InspectionRecord::getCreateTime, LocalDate.parse(dateEnd).atTime(23, 59, 59));
        }
        // 检测员只看本部门单据，主管/管理员看全部
        if ("SELF".equals(UserContext.dataScope())) {
            w.eq(InspectionRecord::getInspectorId, UserContext.userId());
        }
        w.orderByDesc(InspectionRecord::getCreateTime);

        Page<InspectionRecord> p = inspectMapper.selectPage(new Page<>(page, size), w);
        List<Map<String, Object>> rows = p.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("inspectNo", r.getInspectNo());
            m.put("sampleId", r.getSampleId());
            m.put("sampleNo", r.getSampleNo());
            m.put("foodName", r.getFoodName());
            m.put("inspectType", r.getInspectType());
            m.put("inspectMethod", r.getInspectMethod());
            m.put("itemCount", r.getItemCount());
            m.put("result", r.getResult());
            m.put("resultLabel", resultLabel(r.getResult()));
            m.put("status", r.getStatus());
            m.put("statusLabel", Status.inspectLabel(r.getStatus()));
            m.put("inspectorId", r.getInspectorId());
            m.put("inspectorName", r.getInspectorName());
            m.put("inspectTime", r.getInspectTime());
            m.put("reviewTime", r.getReviewTime());
            m.put("reportFile", r.getReportFile());
            m.put("remark", r.getRemark());
            m.put("createTime", r.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        return PageResult.of(p.getTotal(), page, size, rows);
    }

    private String resultLabel(String r) {
        return switch (r == null ? "" : r) {
            case "QUALIFIED" -> "合格";
            case "UNQUALIFIED" -> "不合格";
            case "RECHECK" -> "待复检";
            default -> "—";
        };
    }

    public Map<String, Object> detail(Long id) {
        InspectionRecord r = inspectMapper.selectById(id);
        if (r == null) {
            throw new BizException("检测记录不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("record", r);
        m.put("items", itemMapper.selectList(new LambdaQueryWrapper<InspectionItem>()
                .eq(InspectionItem::getRecordId, id).orderByAsc(InspectionItem::getId)));
        FoodSample s = sampleMapper.selectById(r.getSampleId());
        m.put("sample", s);
        m.put("incident", incidentMapper.selectOne(new LambdaQueryWrapper<FoodIncident>()
                .eq(FoodIncident::getSampleId, r.getSampleId())
                .ne(FoodIncident::getStatus, Status.INC_CLOSED)
                .orderByDesc(FoodIncident::getOccurTime).last("limit 1")));
        return m;
    }

    /** 新建检测单（待检测）。同一留样存在未完成的检测单时拒绝重复建单。 */
    @Transactional
    public Long create(InspectSaveDTO dto) {
        FoodSample s = sampleMapper.selectById(dto.getSampleId());
        if (s == null) {
            throw new BizException("留样记录不存在");
        }
        long open = inspectMapper.selectCount(new LambdaQueryWrapper<InspectionRecord>()
                .eq(InspectionRecord::getSampleId, dto.getSampleId())
                .in(InspectionRecord::getStatus, Status.INS_PENDING, Status.INS_TESTING, Status.INS_REVIEW));
        if (open > 0) {
            throw new BizException("该留样已存在未完成的检测单，请勿重复建单");
        }
        InspectionRecord r = new InspectionRecord();
        r.setInspectNo("JC" + LocalDate.now().format(DAY) + "-" +
                String.format("%04d", inspectMapper.selectCount(new LambdaQueryWrapper<InspectionRecord>()
                        .ge(InspectionRecord::getCreateTime, LocalDate.now().atStartOfDay())) + 1));
        r.setSampleId(s.getId());
        r.setSampleNo(s.getSampleNo());
        r.setFoodName(s.getFoodName());
        r.setInspectType(dto.getInspectType() == null ? "综合" : dto.getInspectType());
        r.setInspectMethod(dto.getInspectMethod() == null ? "随机抽检" : dto.getInspectMethod());
        r.setItemCount(dto.getItems() == null ? 0 : dto.getItems().size());
        r.setResult(Status.INS_PENDING);
        r.setStatus(Status.INS_PENDING);
        r.setInspectorId(UserContext.userId());
        r.setInspectorName(UserContext.realName());
        r.setRemark(dto.getRemark());
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        inspectMapper.insert(r);
        saveItems(r.getId(), dto.getItems());
        logService.log("新建检测单", r.getInspectNo(), s.getFoodName() + " " + r.getInspectType());
        return r.getId();
    }

    /**
     * 录入结果并提交。
     * 不合格 → 自动建事件 + 锁定留样；待复检 → 留样结论置 RECHECK 但锁定解除（需重新检测）。
     */
    @Transactional
    public void submit(Long id, InspectSaveDTO dto) {
        InspectionRecord r = inspectMapper.selectById(id);
        if (r == null) {
            throw new BizException("检测记录不存在");
        }
        if (!Status.INS_PENDING.equals(r.getStatus()) && !Status.INS_TESTING.equals(r.getStatus())) {
            throw new BizException("当前状态（" + Status.inspectLabel(r.getStatus()) + "）不允许录入结果");
        }
        String result = dto.getResult() == null ? "QUALIFIED" : dto.getResult();
        if (!List.of("QUALIFIED", "UNQUALIFIED", "RECHECK").contains(result)) {
            throw new BizException("综合结论不合法");
        }
        itemMapper.delete(new LambdaQueryWrapper<InspectionItem>().eq(InspectionItem::getRecordId, id));
        saveItems(id, dto.getItems());

        r.setResult(result);
        r.setStatus(Status.INS_REVIEW);          // 提交后进入待复核
        r.setItemCount(dto.getItems() == null ? 0 : dto.getItems().size());
        r.setInspectTime(LocalDateTime.now());
        r.setReportFile(dto.getReportFile());
        r.setRemark(dto.getRemark());
        r.setUpdateTime(LocalDateTime.now());
        inspectMapper.updateById(r);
        logService.log("提交检测结果", r.getInspectNo(), "结论 " + resultLabel(result));
    }

    /**
     * 主管复核检测结果：通过后回写留样结论；不合格自动生成事件并锁定样品。
     */
    @Transactional
    public Long review(Long id, boolean pass, String remark) {
        InspectionRecord r = inspectMapper.selectById(id);
        if (r == null) {
            throw new BizException("检测记录不存在");
        }
        if (!Status.INS_REVIEW.equals(r.getStatus())) {
            throw new BizException("仅待复核的检测单可以复核");
        }
        if (!pass) {
            if (remark == null || remark.trim().length() < 5) {
                throw new BizException("退回原因不少于 5 个字");
            }
            r.setStatus(Status.INS_TESTING);
            r.setRemark(remark.trim());
            r.setUpdateTime(LocalDateTime.now());
            inspectMapper.updateById(r);
            logService.log("检测结果退回", r.getInspectNo(), remark.trim(), false);
            return null;
        }

        FoodSample s = sampleMapper.selectById(r.getSampleId());
        Long incidentId = null;
        if ("UNQUALIFIED".equals(r.getResult())) {
            r.setStatus(Status.INS_FAILED);
            if (s != null) {
                s.setInspectResult("FAILED");
                s.setLocked(1);
                s.setUpdateTime(LocalDateTime.now());
                sampleMapper.updateById(s);
            }
            // 不合格自动生成事件，记录封存与停供
            FoodIncident inc = new FoodIncident();
            inc.setIncidentNo("INC" + LocalDate.now().format(DAY) + "-" +
                    String.format("%03d", incidentMapper.selectCount(new LambdaQueryWrapper<FoodIncident>()
                            .ge(FoodIncident::getCreateTime, LocalDate.now().atStartOfDay())) + 1));
            inc.setSampleId(r.getSampleId());
            inc.setSampleNo(r.getSampleNo());
            inc.setFoodName(r.getFoodName());
            inc.setIncidentType(inferIncidentType(r.getInspectType()));
            inc.setSeverity("MAJOR".equals(r.getInspectType()) ? "MAJOR" : "SERIOUS");
            inc.setStatus(Status.INC_HANDLING);
            inc.setOccurTime(LocalDateTime.now());
            inc.setDiscoverer(UserContext.realName());
            inc.setSealLocation(s == null ? null : s.getLocationCode());
            inc.setStopSupply("同批次相关菜品已撤柜停供");
            inc.setTraceBatch("追溯批次 " + (s == null ? "-" : s.getBatchNo()));
            inc.setCreateTime(LocalDateTime.now());
            inc.setUpdateTime(LocalDateTime.now());
            incidentMapper.insert(inc);
            incidentId = inc.getId();
            sampleService.writeRecord(r.getSampleId(), "LOCK", s == null ? null : s.getStatus(),
                    s == null ? null : s.getStatus(), "检测不合格，样品锁定，禁止销毁");
            logService.notify(7L, "不合格事件待处置",
                    r.getFoodName() + "（" + r.getSampleNo() + "）检测不合格，已锁定样品", "INCIDENT", inc.getId());
        } else if ("RECHECK".equals(r.getResult())) {
            r.setStatus(Status.INS_RECHECK);
            if (s != null) {
                s.setInspectResult("RECHECK");
                s.setUpdateTime(LocalDateTime.now());
                sampleMapper.updateById(s);
            }
        } else {
            r.setStatus(Status.INS_PASSED);
            if (s != null) {
                s.setInspectResult("PASSED");
                s.setUpdateTime(LocalDateTime.now());
                sampleMapper.updateById(s);
            }
        }
        r.setReviewerId(UserContext.userId());
        r.setReviewTime(LocalDateTime.now());
        if (remark != null && !remark.isBlank()) {
            r.setRemark(remark.trim());
        }
        r.setUpdateTime(LocalDateTime.now());
        inspectMapper.updateById(r);
        logService.log("复核检测结果", r.getInspectNo(), "结论 " + resultLabel(r.getResult()));
        return incidentId;
    }

    private String inferIncidentType(String inspectType) {
        return switch (inspectType == null ? "" : inspectType) {
            case "微生物" -> "微生物超标";
            case "添加剂" -> "添加剂超限";
            case "感官" -> "感官异常";
            default -> "综合检测异常";
        };
    }

    private void saveItems(Long recordId, List<InspectSaveDTO.Item> items) {
        if (items == null) {
            return;
        }
        for (InspectSaveDTO.Item it : items) {
            InspectionItem e = new InspectionItem();
            e.setRecordId(recordId);
            e.setItemName(it.getItemName());
            e.setStandardValue(it.getStandardValue());
            e.setActualValue(it.getActualValue());
            e.setJudge(it.getJudge() == null ? "QUALIFIED" : it.getJudge());
            e.setCreateTime(LocalDateTime.now());
            itemMapper.insert(e);
        }
    }

    /** 首页统计：各结果数量 */
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String s : List.of(Status.INS_PENDING, Status.INS_TESTING, Status.INS_REVIEW,
                Status.INS_PASSED, Status.INS_FAILED, Status.INS_RECHECK)) {
            m.put(s, inspectMapper.selectCount(new LambdaQueryWrapper<InspectionRecord>()
                    .eq(InspectionRecord::getStatus, s)));
        }
        m.put("total", inspectMapper.selectCount(null));
        return m;
    }
}
