package local;

import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface ConfirmRepository extends PagingAndSortingRepository<Confirm, Long>{
    List<Confirm> findByMuseumId(String MuseumId);
    Confirm findByBookId(Long BookId);
}