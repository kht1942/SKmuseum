package local;

import local.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PolicyHandler{

    @Autowired
    BookRepository bookRepository;
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }


    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverMuseumDeleted_ForceCancel(@Payload MuseumDeleted museumDeleted){

        if(museumDeleted.isMe()){
            System.out.println("##### listener ForceCancel : " + museumDeleted.toJson());
            List<Book> list = bookRepository.findByMuseumId(museumDeleted.getId());
            for(Book temp : list){
                // 자신이 취소한게 아니면
                if(!"CANCELED".equals(temp.getStatus())) {
                    temp.setStatus("FORCE_CANCELED");
                    bookRepository.save(temp);
                }
            }
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverProductionCompleted_RequestComplete(@Payload ConfirmCompleted productionCompleted){

        if(productionCompleted.isMe()){
            System.out.println("##### listener RequestComplete : " + productionCompleted.toJson());

            Optional<Book> temp = bookRepository.findById(productionCompleted.getBookId());
            Book target = temp.get();
            System.out.println("##### RequestComplete : " + target.toString());
            target.setMuseumId(Long.parseLong(productionCompleted.getMuseumId()));
            target.setStatus(productionCompleted.getStatus());
            bookRepository.save(target);
        }
    }

}
