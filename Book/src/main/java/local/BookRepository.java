package local;

import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface BookRepository extends PagingAndSortingRepository<Book, Long>{

    List<Book> findByMuseumId(Long MuseumId);
}