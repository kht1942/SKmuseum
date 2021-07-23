
package local.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="Museum", url="${api.museum.url}")//,fallback = MuseumrviceFallback.class)
public interface MuseumService {

    @RequestMapping(method= RequestMethod.PUT, value="/museums/{museumId}", consumes = "application/json")
    public void bookRequest(@PathVariable("museumId") Long museumId, @RequestBody Museum museum);

}