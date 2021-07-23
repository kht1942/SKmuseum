
package local.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Component
public class MuseumServiceFallback implements MuseumService {

    @Override
    public void bookRequest(Long museumId, Museum museum) {
        System.out.println("Circuit breaker has been opened. Fallback returned instead.");
    }
}