package local;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Confirm_table")
public class Confirm {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long bookId;
    private String museumId;
    private String name;
    private String custNm;
    private String status;

    @PostPersist
    public void onPostPersist(){
        ConfirmCompleted confirmCompleted = new ConfirmCompleted();
        BeanUtils.copyProperties(this, confirmCompleted);
        confirmCompleted.publishAfterCommit();
    }

    @PostUpdate
    public void onPostUpdate(){
        ConfirmChanged confirmChanged = new ConfirmChanged();
        BeanUtils.copyProperties(this, confirmChanged);
        confirmChanged.publishAfterCommit();
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }
    public String getMuseumId() {
        return museumId;
    }

    public void setMuseumId(String museumId) {
        this.museumId = museumId;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getCustNm() {
        return custNm;
    }

    public void setCustNm(String custNm) {
        this.custNm = custNm;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }




}
