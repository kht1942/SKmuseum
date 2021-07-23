package local;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Museum_table")
public class Museum {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String name;
    private Long pCnt;
    private String chkDate;

    @PostPersist
    public void onPostPersist(){
        MuseumRegistered museumRegistered = new MuseumRegistered();
        BeanUtils.copyProperties(this, museumRegistered);
        museumRegistered.publishAfterCommit();
    }

    @PreUpdate
    public  void onPreUpdate(){
        /*
        강제적 Delay
        try {
            Thread.currentThread().sleep((long)10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        */
    }

    @PostUpdate
    public void onPostUpdate(){

        MuseumChanged museumChanged = new MuseumChanged();
        BeanUtils.copyProperties(this, museumChanged);
        museumChanged.publishAfterCommit();
    }


    @PreRemove
    public void onPreRemove(){
        MuseumDeleted museumDeleted = new MuseumDeleted();
        BeanUtils.copyProperties(this, museumDeleted);
        museumDeleted.publishAfterCommit();

    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    public Long getPCnt() {
        return pCnt;
    }

    public void setPCnt(Long pCnt) {
        this.pCnt = pCnt;
    }
    public String getChkDate() {
        return chkDate;
    }

    public void setChkDate(String chkDate) {
        this.chkDate = chkDate;
    }




}
