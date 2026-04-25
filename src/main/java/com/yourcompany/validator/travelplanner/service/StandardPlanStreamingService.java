package com.yourcompany.validator.travelplanner.service;

import com.yourcompany.validator.travelplanner.dto.AiPlanStreamEvent;
import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.dto.PlanResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;

@Service
public class StandardPlanStreamingService {
    private final TravelPlanningService travelPlanningService;

    public StandardPlanStreamingService(TravelPlanningService travelPlanningService) {
        this.travelPlanningService = travelPlanningService;
    }

    public SseEmitter streamPlan(PlanRequest request) {
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                int totalDays = travelPlanningService.getPlanDays(request);
                send(emitter, "status", 5, "已开始整理普通规划，正在检查你的景点输入。", null, null);

                for (int day = 1; day <= totalDays; day++) {
                    int progress = Math.min(95, 10 + (day * 80 / Math.max(1, totalDays)));
                    send(
                            emitter,
                            "status",
                            progress,
                            "正在整理第 " + day + " 天：匹配景点、排序并准备地图预览。",
                            null,
                            null
                    );

                    PlanResponse preview = travelPlanningService.previewStandardPlan(request, day);
                    send(
                            emitter,
                            "preview",
                            progress,
                            "已完成第 " + day + " 天，共 " + totalDays + " 天。",
                            preview,
                            null
                    );
                }

                PlanResponse finalPlan = travelPlanningService.createPlan(request);
                send(emitter, "complete", 100, "普通规划已生成完成。", null, finalPlan);
                emitter.complete();
            } catch (Exception exception) {
                try {
                    send(emitter, "error", 100, exception.getMessage(), null, null);
                } catch (Exception ignored) {
                }
                emitter.completeWithError(exception);
            }
        });

        return emitter;
    }

    private void send(SseEmitter emitter,
                      String type,
                      int progress,
                      String message,
                      PlanResponse preview,
                      PlanResponse plan) throws Exception {
        emitter.send(SseEmitter.event()
                .name(type)
                .data(new AiPlanStreamEvent(type, progress, message, preview, plan), MediaType.APPLICATION_JSON));
    }
}
