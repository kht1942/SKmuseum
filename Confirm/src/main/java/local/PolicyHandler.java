package local;

import local.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PolicyHandler{

    @Autowired
    ConfirmRepository confirmRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverBookdRequested_ConfirmComplete(@Payload Requested requested){

        if(requested.isMe()){
            //  주문 요청으로 인한 제조 확정
            System.out.println("##### 주문 요청으로 인한 제조 확정: " + requested.toJson());
            if(requested.isMe()){
                Confirm temp = new Confirm();
                temp.setStatus("REQUEST_COMPLETED");
                temp.setCustNm(requested.getCustNm());
                temp.setMuseumId(requested.getMuseumId());
                temp.setName(requested.getName());
                temp.setBookId(requested.getId());
                confirmRepository.save(temp);
            }
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverMuseumDeleted_ForcedConfirmCancel(@Payload MuseumDeleted museumDeleted){

        if(museumDeleted.isMe()){
            System.out.println("##### listener ForcedConfirmCanceled : " + museumDeleted.toJson());
            //  카페 종료로 인해 제조 상태 변경
            List<Confirm> list = confirmRepository.findByMuseumId(String.valueOf(museumDeleted.getId()));
            for(Confirm temp : list){
                if(!"CANCELED".equals(temp.getStatus())) {
                    temp.setStatus("FORCE_CANCELED");
                    confirmRepository.save(temp);
                }
            }
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCanceled_ConfirmCancel(@Payload Canceled canceled){

        if(canceled.isMe()){
            //  주문 취소로 인한 취소
            Confirm temp = confirmRepository.findByBookId(canceled.getId());
            temp.setStatus("CANCELED");
            confirmRepository.save(temp);

        }
    }

}
