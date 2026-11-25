package com.grad.food.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.grad.food.common.Status;
import com.grad.food.entity.FoodSample;
import com.grad.food.entity.FoodSampleRecord;
import com.grad.food.entity.SampleTask;
import com.grad.food.mapper.FoodSampleMapper;
import com.grad.food.mapper.FoodSampleRecordMapper;
import com.grad.food.mapper.SampleTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务：计算留样到期与任务逾期（需求 3「后端定时计算留样到期时间、逾期未销毁和缺失记录」）。
 *
 * 每小时执行一次：
 *  1. 保留中且已过保留时限 → 待销毁，并写时间轴；
 *  2. 待执行任务超过截止时间 → 逾期；
 *  3. 记录日志，便于在操作日志里追溯系统行为。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final FoodSampleMapper sampleMapper;
    private final FoodSampleRecordMapper recordMapper;
    private final SampleTaskMapper taskMapper;

    @Scheduled(cron = "${app.expire-cron:0 5 * * * ?}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();

        // 1. 到期转待销毁
        List<FoodSample> expired = sampleMapper.selectList(new LambdaQueryWrapper<FoodSample>()
                .eq(FoodSample::getStatus, Status.RETAINING)
                .lt(FoodSample::getExpireTime, now));
        for (FoodSample s : expired) {
            s.setStatus(Status.PENDING_DISPOSE);
            s.setUpdateTime(now);
            sampleMapper.updateById(s);
            FoodSampleRecord r = new FoodSampleRecord();
            r.setSampleId(s.getId());
            r.setAction("EXPIRE");
            r.setFromStatus(Status.RETAINING);
            r.setToStatus(Status.PENDING_DISPOSE);
            r.setOperatorName("系统");
            r.setReason("到达保留时限，自动转入待销毁");
            r.setCreateTime(now);
            recordMapper.insert(r);
        }

        // 2. 任务逾期
        List<SampleTask> overdue = taskMapper.selectList(new LambdaQueryWrapper<SampleTask>()
                .eq(SampleTask::getStatus, Status.PENDING)
                .lt(SampleTask::getDeadline, now));
        for (SampleTask t : overdue) {
            t.setStatus(Status.OVERDUE);
            t.setRemark("超过截止时间未完成留样，系统自动标记逾期");
            t.setUpdateTime(now);
            taskMapper.updateById(t);
        }

        if (!expired.isEmpty() || !overdue.isEmpty()) {
            log.info("到期扫描：转入待销毁 {} 份，任务逾期 {}", expired.size(), overdue.size());
        }
    }
}
