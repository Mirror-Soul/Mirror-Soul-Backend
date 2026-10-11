package com.mirrorsoul.mirrorsoul_api.overview;

import com.mirrorsoul.mirrorsoul_api.access.AdminPermissionService;
import com.mirrorsoul.mirrorsoul_api.common.query.AdminListWindow;
import java.time.OffsetDateTime;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.mirrorsoul.mirrorsoul_api.overview.OverviewDtos.*;

@Service @RequiredArgsConstructor
public class AdminOverviewService {
    private final AdminPermissionService permissions;
    private final OverviewCountReader reader;
    private final OverviewTelemetry telemetry;
    @Value("${quality.training-stall-seconds:900}") private long stallSeconds;

    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Overview get(Long actor,OffsetDateTime from,OffsetDateTime to) {
        long started=System.nanoTime();
        var allowed=permissions.permissions(actor);
        var asOf=AdminListWindow.now();
        var period=new AdminListWindow(from,to==null?asOf:to);
        var cutoff=asOf.minusSeconds(stallSeconds);
        List<Card> cards=new ArrayList<>();
        for(var key:Key.values()) {
            if(!allowed.contains(key.permission))continue;
            long queryStarted=System.nanoTime();
            Long count=null;boolean success=false;
            try {
                count=reader.count(key,period,AdminListWindow.local(cutoff));
                if(count<0)throw new IllegalStateException("Invalid aggregate count");
                success=true;
            } catch(RuntimeException ignored) {
                count=null;
            }
            long duration=System.nanoTime()-queryStarted;
            telemetry.card(key,success,duration);
            cards.add(new Card(key,count,success?DataState.READY:DataState.UNAVAILABLE,AdminListWindow.now(),
                    TimeUnit.NANOSECONDS.toMillis(duration),success?null:"QUERY_FAILED",drilldown(key,period,cutoff)));
        }
        long failed=cards.stream().filter(c->c.dataState()==DataState.UNAVAILABLE).count();
        State state=failed==0?State.COMPLETE:failed==cards.size()?State.UNAVAILABLE:State.PARTIAL;
        telemetry.request(state,cards.size(),System.nanoTime()-started);
        return new Overview(asOf,AdminListWindow.now(),period,"CREATED_AT","DIRECT","LIVE_PER_CARD",state,List.copyOf(cards));
    }

    private Drilldown drilldown(Key key,AdminListWindow period,OffsetDateTime cutoff) {
        Map<String,String> query=new LinkedHashMap<>();String path;
        switch(key) {
            case PENDING_DOCUMENTS -> path="/admin/job-verifications";
            case URGENT_REPORTS -> {path="/admin/reports";query.put("priority","URGENT");query.put("unresolvedOnly","true");}
            case UNANSWERED_SUPPORT -> {path="/admin/support-tickets";query.put("awaitingReply","true");}
            case FAILED_TRAINING_JOBS -> {path="/admin/clone-training-jobs";query.put("status","FAILED");query.put("unresolvedOnly","true");}
            case STALLED_TRAINING_JOBS -> {path="/admin/clone-training-jobs";query.put("stalledBefore",cutoff.toString());}
            default -> throw new IllegalStateException();
        }
        if(period.from()!=null)query.put("from",period.from().toString());
        query.put("to",period.to().toString());query.put("page","0");query.put("size","20");
        String href=path+"?"+query.entrySet().stream()
                .map(e->e.getKey()+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("&"));
        return new Drilldown("GET",path,Map.copyOf(query),href);
    }
}
