package local;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;

@RestController
public class MuseumController {

    @Autowired
    MuseumRepository museumRepository;


    @PostMapping("/museums")
    void museumInsert(@RequestBody Museum data) {
        museumRepository.save(data);
    }

    @PutMapping("/museums/{museumId}")
    void decreasePcnt(@PathVariable(value = "museumId") Long museumId) {

        Optional<Museum> a = museumRepository.findById(museumId);
        if (a.isPresent()) {
            Museum b = a.get();
            b.setPCnt(b.getPCnt() - 1);
            museumRepository.save(b);
        }
    }


    @GetMapping("/museums")
    Iterable<Museum> getMuseumList() {
        Iterable<Museum> result = museumRepository.findAll();
        return result;
    }

    @GetMapping("/museums/{museumId}")
    Museum getMuseumById(@PathVariable(value = "museumId") Long museumId) {
        System.out.println("productStockCheck call");
        Optional<Museum> a = museumRepository.findById(museumId);
        return a.get();
    }


    @DeleteMapping("/museums/{museumId}")
    void museumDelete(@PathVariable(value = "museumId") Long museumId) {
        museumRepository.deleteById(museumId);

    }

}
