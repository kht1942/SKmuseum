package local;

public class BookCanceld extends AbstractEvent {

    private Long id;
    private String museumId;
    private String name;
    private Long pCnt;
    private String chkDate;

    public BookCanceld(){
        super();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getMuseumId() {
        return museumId;
    }

    public void setMuseumId(String nuseumId) {
        this.museumId = nuseumId;
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
