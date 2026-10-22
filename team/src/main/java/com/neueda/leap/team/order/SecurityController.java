package com.neueda.leap.team.order;

import com.neueda.leap.team.dto.PageResponse;
import com.neueda.leap.team.exception.ApiException;
import com.neueda.leap.team.market.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/securities")
@SecurityRequirement(name="bearerAuth")
@Tag(name="Securities")
@PreAuthorize("hasAnyRole('CLIENT','ADMIN')")
public class SecurityController {
    private final OrderRepository repo;
    private final MarketDataClient market;
    private final DailyCloseCache closes;
    public SecurityController(OrderRepository repo,MarketDataClient market,DailyCloseCache closes){
        this.repo=repo;this.market=market;this.closes=closes;
    }
    @GetMapping
    public PageResponse<SecurityDto> list(@RequestParam(required=false)String q,@RequestParam(defaultValue="ACTIVE")String status,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return repo.securities(q,status,page,size);}
    @GetMapping("/{securityId}")
    public SecurityDto get(@PathVariable long securityId){return repo.security(securityId,false);}
    @GetMapping("/{securityId}/quote")
    public MarketQuote quote(@PathVariable long securityId){
        var security=repo.security(securityId,false);
        var result=market.quotes(Set.of(security.ticker())).get(security.ticker());
        if(result==null||result.quote()==null)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                result==null?"MARKET_DATA_UNAVAILABLE":result.errorCode(),"An indicative quote is currently unavailable.");
        // Indicative only: may be stale/closed. Execution separately checks price, currency, time and market state.
        return result.quote();
    }
    @GetMapping("/{securityId}/closes")
    @Operation(summary="Read daily closes for a chart range",
            description="range is 1D, 1W, 1M or 1Y. Raw closes in the quote currency; the first one is the last close before the range starts.")
    public SecurityClosesDto closes(@PathVariable long securityId,@RequestParam(defaultValue="1M")String range){
        var chartRange=ChartRange.parse(range);
        var security=repo.security(securityId,false);
        var history=closes.closes(security.ticker());
        if(history==null)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"MARKET_DATA_UNAVAILABLE","Price history is currently unavailable.");
        var start=chartRange.start(closes.today());
        var baseline=history.lowerKey(start);
        var inRange=history.tailMap(baseline==null?start:baseline,true).entrySet().stream()
                .map(e->new DailyClose(e.getKey(),e.getValue())).toList();
        return new SecurityClosesDto(securityId,security.ticker(),security.quoteCurrency(),chartRange.code(),inRange);
    }
}
