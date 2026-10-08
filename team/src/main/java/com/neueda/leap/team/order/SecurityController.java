package com.neueda.leap.team.order;

import com.neueda.leap.team.dto.PageResponse;
import com.neueda.leap.team.exception.ApiException;
import com.neueda.leap.team.market.*;
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
    public SecurityController(OrderRepository repo,MarketDataClient market){this.repo=repo;this.market=market;}
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
}
