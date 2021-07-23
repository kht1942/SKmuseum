package local;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

@Entity
@Table(name="Book_table")
public class Book {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long museumId;
    private String chkDate;
    private String custNm;
    private String status;
    private String name;

    @PostPersist
    public void onPostPersist(){;

        // 주문 요청함 ( Req / Res : 동기 방식 호출)
        local.external.Museum museum = new local.external.Museum();
        museum.setMuseumId(getMuseumId());
        // mappings goes here
        BookManageApplication.applicationContext.getBean(local.external.MuseumService.class)
            .bookRequest(museum.getMuseumId(),museum);


        Requested requested = new Requested();
        BeanUtils.copyProperties(this, requested);
        requested.publishAfterCommit();
    }


    @PostUpdate
    public void onPostUpdate(){

        System.out.println("#### onPostUpdate :" + this.toString());

        if("CANCELED".equals(this.getStatus())) {
            Canceled canceled = new Canceled();
            BeanUtils.copyProperties(this, canceled);
            canceled.publishAfterCommit();
        }
        else if("FORCE_CANCELED".equals(getStatus())){
            ForceCanceled forceCanceled = new ForceCanceled();
            BeanUtils.copyProperties(this, forceCanceled);
            forceCanceled.publishAfterCommit();
        }
        else if("REQUEST_COMPLETED".equals(getStatus())){
            System.out.println(getStatus());
            System.out.println("## REQ Info : " + this.getMuseumId());
            System.out.println("## REQ Info : " + this.getMuseumId());
            RequestCompleted requestCompleted = new RequestCompleted();
            BeanUtils.copyProperties(this, requestCompleted);
            requestCompleted.publishAfterCommit();
        }

    }



    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getMuseumId() {
        return museumId;
    }

    public void setMuseumId(Long museumId) {
        this.museumId = museumId;
    }
    public String getChkDate() {
        return chkDate;
    }

    public void setChkDate(String chkDate) {
        this.chkDate = chkDate;
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
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }




}
