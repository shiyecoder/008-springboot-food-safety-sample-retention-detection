package com.grad.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.grad.food.common.BizException;
import com.grad.food.common.PageResult;
import com.grad.food.common.Status;
import com.grad.food.common.UserContext;
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
 * 留样任务服务。
 * 任务由「批次 + 留样规则」自动生成，生成时把规则快照（最低重量、存储条件）固化到任务上，
 * 之后修改规则不影响已生成任务，避免历史任务标准被追溯篡改。
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final SampleTaskMapper taskMapper;
    private final FoodBatchMapper batchMapper;
    private final SampleRuleMapper ruleMapper;
    private final FoodCategoryMapper categoryMapper;
    private final FoodSampleMapper sampleMapper;
    private final LogService logService;

    /** 任务列表：按状态、餐次、日期、执行人过滤 */
    public PageResult<Map<String, Object>> page(long page, long size, String status, String meal,
                                                String date, String keyword, String tab) {
        LambdaQueryWrapper<SampleTask> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            w.eq(SampleTask::getStatus, status);
        }
        if (tab != null && !tab.isBlank()) {
            if ("DONE".equals(tab)) {
                w.in(SampleTask::getStatus, Status.RETAINING, Status.PENDING_DISPOSE, Status.DISPOSED);
            } else if ("TODO".equals(tab)) {
                w.in(SampleTask::getStatus, Status.PENDING, Status.OVERDUE);
            } else {
                w.eq(SampleTask::getStatus, tab);
            }
        }
        if (meal != null && !meal.isBlank()) {
            w.eq(SampleTask::getMeal, meal);
        }
        if (date != null && !date.isBlank()) {
            w.ge(SampleTask::getCreateTime, LocalDate.parse(date).atStartOfDay())
                    .le(SampleTask::getCreateTime, LocalDate.parse(date).atTime(23, 59, 59));
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SampleTask::getFoodName, keyword).or().like(SampleTask::getTaskNo, keyword));
        }
        // 留样员只看自己的任务
        if ("SELF".equals(UserContext.dataScope())) {
            w.eq(SampleTask::getSamplerId, UserContext.userId());
        }
        w.orderByDesc(SampleTask::getDeadline);

        Page<SampleTask> p = taskMapper.selectPage(new Page<>(page, size), w);
        Map<Long, String> catNames = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(FoodCategory::getId, FoodCategory::getName));
        List<Map<String, Object>> rows = p.getRecords().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskNo", t.getTaskNo());
            m.put("batchNo", t.getBatchNo());
            m.put("foodName", t.getFoodName());
            m.put("categoryId", t.getCategoryId());
            m.put("categoryName", catNames.get(t.getCategoryId()));
            m.put("meal", t.getMeal());
            m.put("minWeight", t.getMinWeight());
            m.put("storageCond", t.getStorageCond());
            m.put("deadline", t.getDeadline());
            m.put("status", t.getStatus());
            m.put("statusLabel", Status.sampleLabel(t.getStatus()));
            m.put("samplerId", t.getSamplerId());
            m.put("samplerName", t.getSamplerName());
            m.put("sampleId", t.getSampleId());
            m.put("remark", t.getRemark());
            // 距离截止不足 40 分钟的任务标红
            m.put("urgent", Status.PENDING.equals(t.getStatus()) && t.getDeadline() != null
                    && t.getDeadline().isBefore(LocalDateTime.now().plusMinutes(40)));
            return m;
        }).collect(Collectors.toList());
        return PageResult.of(p.getTotal(), page, size, rows);
    }

    /** 今日待办任务（留样员首页用），含逾期与即将超时统计 */
    public Map<String, Object> today() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(23, 59, 59);
        // 主管/管理员看全部任务，留样员只看自己的
        LambdaQueryWrapper<SampleTask> base = new LambdaQueryWrapper<SampleTask>();
        if ("SELF".equals(UserContext.dataScope())) {
            base.eq(SampleTask::getSamplerId, UserContext.userId());
        }
        List<SampleTask> all = taskMapper.selectList(base.clone()
                .ge(SampleTask::getCreateTime, start).le(SampleTask::getCreateTime, end));

        long pending = all.stream().filter(t -> Status.PENDING.equals(t.getStatus())).count();
        long urgent = all.stream().filter(t -> Status.PENDING.equals(t.getStatus())
                && t.getDeadline() != null && t.getDeadline().isBefore(LocalDateTime.now().plusMinutes(40))).count();
        long review = all.stream().filter(t -> Status.PENDING_REVIEW.equals(t.getStatus())).count();
        long done = all.stream().filter(t -> !Status.PENDING.equals(t.getStatus())
                && !Status.OVERDUE.equals(t.getStatus())).count();
        long overdue = taskMapper.selectCount(base.clone().eq(SampleTask::getStatus, Status.OVERDUE));
        long monthDone = taskMapper.selectCount(new LambdaQueryWrapper<SampleTask>()
                .eq("SELF".equals(UserContext.dataScope()), SampleTask::getSamplerId, UserContext.userId())
                .ge(SampleTask::getCreateTime, LocalDate.now().withDayOfMonth(1).atStartOfDay())
                .in(SampleTask::getStatus, Status.RETAINING, Status.PENDING_DISPOSE, Status.DISPOSED));

        List<Map<String, Object>> todo = all.stream()
                .filter(t -> Status.PENDING.equals(t.getStatus()) || Status.OVERDUE.equals(t.getStatus()))
                .sorted(Comparator.comparing(SampleTask::getDeadline))
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("taskNo", t.getTaskNo());
                    m.put("foodName", t.getFoodName());
                    m.put("meal", t.getMeal());
                    m.put("batchNo", t.getBatchNo());
                    m.put("minWeight", t.getMinWeight());
                    m.put("storageCond", t.getStorageCond());
                    m.put("deadline", t.getDeadline());
                    m.put("status", t.getStatus());
                    m.put("statusLabel", Status.sampleLabel(t.getStatus()));
                    m.put("urgent", Status.PENDING.equals(t.getStatus()) && t.getDeadline() != null
                            && t.getDeadline().isBefore(LocalDateTime.now().plusMinutes(40)));
                    return m;
                }).collect(Collectors.toList());

        // 本周完成量与平均重量
        List<SampleTask> week = taskMapper.selectList(base.clone()
                .ge(SampleTask::getCreateTime, LocalDate.now().minusDays(7).atStartOfDay()));
        long weekDone = week.stream().filter(t -> t.getSampleId() != null).count();
        List<FoodSample> samples = sampleMapper.selectList(new LambdaQueryWrapper<FoodSample>()
                .eq("SELF".equals(UserContext.dataScope()), FoodSample::getSamplerId, UserContext.userId())
                .ge(FoodSample::getStoreTime, LocalDate.now().minusDays(7).atStartOfDay()));
        double avg = samples.stream().filter(s -> s.getWeight() != null)
                .mapToDouble(s -> s.getWeight().doubleValue()).average().orElse(0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pending", pending);
        data.put("urgent", urgent);
        data.put("review", review);
        data.put("done", done);
        data.put("overdue", overdue);
        data.put("monthDone", monthDone);
        data.put("weekDone", weekDone);
        data.put("avgWeight", Math.round(avg * 10) / 10.0);
        data.put("todo", todo);
        return data;
    }

    /**
     * 按批次生成留样任务。
     * items 为空时按该批次分类 + 全部启用的留样规则生成，保证不漏项；
     * 同一批次内同名任务不重复生成。
     */
    @Transactional
    public int generate(Long batchId, List<Map<String, Object>> items) {
        FoodBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException("批次不存在");
        }
        List<Map<String, Object>> targets = items;
        if (targets == null || targets.isEmpty()) {
            targets = categoryMapper.selectList(new LambdaQueryWrapper<FoodCategory>()
                            .eq(FoodCategory::getStatus, 1).orderByAsc(FoodCategory::getSort))
                    .stream().map(c -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("foodName", c.getName());
                        m.put("categoryId", c.getId());
                        return m;
                    }).collect(Collectors.toList());
        }
        int created = 0;
        for (Map<String, Object> it : targets) {
            String foodName = String.valueOf(it.get("foodName"));
            Long catId = Long.valueOf(String.valueOf(it.get("categoryId")));
            long exists = taskMapper.selectCount(new LambdaQueryWrapper<SampleTask>()
                    .eq(SampleTask::getBatchId, batchId).eq(SampleTask::getFoodName, foodName));
            if (exists > 0) {
                continue;
            }
            SampleRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<SampleRule>()
                    .eq(SampleRule::getCategoryId, catId).eq(SampleRule::getStatus, 1).last("limit 1"));
            if (rule == null) {
                continue;
            }
            SampleTask t = new SampleTask();
            t.setTaskNo("RW" + batch.getBatchNo() + "-" + (taskMapper.selectCount(new LambdaQueryWrapper<SampleTask>()
                    .eq(SampleTask::getBatchId, batchId)) + 1));
            t.setBatchId(batchId);
            t.setBatchNo(batch.getBatchNo());
            t.setFoodName(foodName);
            t.setCategoryId(catId);
            t.setMeal(batch.getMeal());
            t.setRuleId(rule.getId());
            t.setMinWeight(rule.getMinWeight());
            t.setStorageCond(rule.getStorageCond());
            // 截止时间：加工时间后 3 小时，且不晚于当天 13:30
            LocalDateTime deadline = batch.getProduceTime().plusHours(3);
            t.setDeadline(deadline);
            t.setStatus(Status.PENDING);
            t.setSamplerId(UserContext.userId());
            t.setSamplerName(UserContext.realName());
            t.setCreateTime(LocalDateTime.now());
            t.setUpdateTime(LocalDateTime.now());
            taskMapper.insert(t);
            created++;
        }
        logService.log("生成留样任务", batch.getBatchNo(), "新增 " + created + " 条任务");
        return created;
    }

    /** 任务统计：用于任务页顶部摘要 */
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String s : List.of(Status.PENDING, Status.PENDING_REVIEW, Status.RETAINING,
                Status.PENDING_DISPOSE, Status.DISPOSED, Status.REJECTED, Status.OVERDUE)) {
            m.put(s, taskMapper.selectCount(new LambdaQueryWrapper<SampleTask>().eq(SampleTask::getStatus, s)));
        }
        m.put("total", taskMapper.selectCount(null));
        return m;
    }
}
