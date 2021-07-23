# AWS 4차 - 개인과제 김형태 : SK Museum

# Table of contents

- [서비스 시나리오](#서비스-시나리오)
- [분석/설계](#분석설계)
  - [Event Storming](#Event-Storming)
  - [헥사고날 아키텍처 다이어그램 도출](#헥사고날-아키텍처-다이어그램-도출)
- [구현](#구현)
  - [시나리오 테스트결과](#시나리오-테스트결과)
  - [DDD의 적용](#DDD의-적용)
  - [Gateway 적용](#Gateway-적용)
  - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
  - [동기식 호출과 Fallback 처리](#동기식-호출과-Fallback-처리)
  - [비동기식 호출과 Eventual Consistency](#비동기식-호출-/-시간적-디커플링-/-장애격리-/-최종-(Eventual)-일관성-테스트)
 - [운영](#운영)
   - [CI/CD 설정](#CI/CD-설정)
   - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식호출-/-서킷브레이킹-/-장애격리)
   - [오토스케일 아웃](#오토스케일-아웃)
   - [무정지 재배포](#무정지-재배포)
   - [ConfigMap 사용](#ConfigMap-사용)



---


# 서비스 시나리오

## 기능적 요구사항
1. 미술관에서 전시(Hall, 날짜, 참석가능인원)을 등록한다.
1. 사용자가 예약을 요청한다.. 
1. 사용자의 예약 요청에 따라서 전시의 참석가능인원이 감소한다. (Sync) 
1. 사용자의 예약 상태가 완료로 변경된다. (Async) 
1. 사용자의 예약 완료에 따라서 입장 확인의 상태가 변경된다.
1. 사용자가 예약을 취소한다.
1. 사용자의 예약 취소에 따라서 전시의 참석가능인원이 증가한다. (Async)
1. 사용자의 예약 취소에 따라서 입장 확인의 상태가 예약 취소로 변경된다.
1. 미술관이 전시를 삭제한다.
1. 미술관의 전시 삭제에 따라서 예약의 상태가 강제취소로 변경된다.
1. 시용자가 예약 상태를 조회한다.

## 비기능적 요구사항
1. 트랜잭션
    1. 사용자의 예약에 따라 해당 날짜 / 참석가능인원 수가 감소한다. > Sync
    1. 사용자의 예약취소에 따라 해당 날짜 / 참석가능인원 수가 증가한다. > Async
1. 장애격리
    1. 입장 확인 서비스에 장애가 발생하더라도 예약은 정상적으로 처리 가능하다.  > Async (event-driven)
    1. 서킷 브레이킹 프레임워크 > istio-injection + DestinationRule
1. 성능
    1. 사용자는 자신의 예약상태를 확인할 수 있다. > CQRS

# 분석/설계

## AS-IS 조직 (Horizontally-Aligned)
![](https://i.imgur.com/iVSYlxP.png)



## TO-BE 조직 (Vertically-Aligned)
![](https://i.imgur.com/1Xby4O1.png)



# Event Storming 

### 이벤트 도출
![](https://i.imgur.com/MgsFBVZ.png)



### 부적격 이벤트 탈락
![](https://i.imgur.com/4GUND5U.png)

    - 과정중 도출된 잘못된 도메인 이벤트들을 걸러내는 작업을 수행함

## Event Storming 결과
Evnt Storming
![](https://i.imgur.com/voiM00g.png)


## 완성본에 대한 기능적/비기능적 요구사항을 커버하는지 검증

### 기능 요구사항 Coverage


1. 시나리오 Coverage check: 1
![](https://i.imgur.com/xqqb0NB.png)

    1. 관리자가 전시회(전시홀, 수용가능인원, 전시날짜)를 등록한다.

2. 시나리오 Coverage check: 2
![](https://i.imgur.com/PqftctL.png)
    1. 사용자가 전시회를 예약한다.
    2. 해당 전시회의 참석가능인원이 감소한다(Sync).
    3. 예약 완료로 변경된다(Async).
    4. 예약의 상태가 변경된다.

3. 시나리오 Coverage check: 3
![](https://i.imgur.com/2sJUknv.png)
    1. 사용자가 예약을 취소한다.
    2. 예약 취소 시, 참석가능인원이 증가한다(Async).
    3. 예약의 상태가 변경된다.

### 비기능적 요구사항 Coverage
![](https://i.imgur.com/sjILsY1.png)

1. 트랜잭션
    1. 사용자의 예약에 의해 참석가능인원이 감소한다. : Sync ①
    2. 사용자의 예약취소에 의해 참석가능인원이 증가한다. : Async ②
3. 장애 격리
    1. 입장 확인에 장애가 발생하더라도 예약은 정상적으로 처리 가능하다. : Async(Event-driven) ③
    2. 서킷 브레이킹 프로엠워크 : istio-injection & DestinationRule 
5. 성능
    1. 사용자는 예약 상태를 확인할 수 있다. : CQRS ④



## 헥사고날 아키텍처 다이어그램 도출
* CQRS 를 위한 Mypage 서비스만 DB를 구분하여 적용
![](https://i.imgur.com/8EmAtHQ.png)

# 구현

## 시나리오 테스트결과

**기능  및 이벤트 Payload**

1. 관리자가 전시정보(전시홀, 참석가능인원, 날짜)를 등록한다. 
![](https://i.imgur.com/WVQIrgi.png)


2. 사용자가 전시회 관람을 예약한다.
![](https://i.imgur.com/dKrquqm.png)


3. 사용자의 예약에 의해 해당 전시의 참석가능인원 수가 감소한다.(Sync)
![](https://i.imgur.com/OokhnBQ.png)


4. 입장확인에 예약 정보가 전달된다.(Async) 
![](https://i.imgur.com/JKEkXkX.png)

5. 사용자가 예약 정보를 조회한다.
![](https://i.imgur.com/MKom8SR.png)


6. 사용자가 예약을 취소한다.
![](https://i.imgur.com/vE6rSzn.png)


7. 사용자의 예약 취소에 의해 전시의 참석가능인원 수가 증가한다.(Async)
![](https://i.imgur.com/eExoi4Z.png)


8. 사용자의 예약 정보가 취소로 변경된다.
![](https://i.imgur.com/fm9SdQJ.png)


9. 관리자가 전시 정보를 삭제한다.
![](https://i.imgur.com/AGDE6ES.png)



10. 전시 삭제에 의해 예약과 입장승인의 상태가 강제취소로 변경된다.
![](https://i.imgur.com/zDUzpL4.png)
![](https://i.imgur.com/XRnhV35.png)


11. 사용자가 예약 상태를 조회한다.
![](https://i.imgur.com/FLlzXRh.png)


## DDD의 적용

분석/설계 단계에서 도출된 MSA는 총 4개로 아래와 같다.
* MyPage 는 CQRS 를 위한 서비스

| MSA | 기능 | port | 조회 API | Gateway 사용시 |
|---|:---:|:---:|---|---|
| Order | 예약 관리 | 8081 | http://localhost:8081/books | http://Bookb:8080/books |
| Cafe  | 전시 관리 | 8082 | http://localhost:8082/museums | http://Museums:8080/museums |
| Production | 입장승인 관리 | 8083 | http://localhost:8083/confirms | http://Confirms:8080/confirms |
| MyPage | my page | 8084 | http://localhost:8084/myPages | http://MyPage:8080/myPages |



## Gateway 적용

```
spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: Books
          uri: http://Books:8080
          predicates:
            - Path=/books/**
        - id: Museums
          uri: http://Museums:8080
          predicates:
            - Path=/museums/** 
        - id: Confirms
          uri: http://Confirms:8080
          predicates:
            - Path=/confirms/** 
        - id: MyPage
          uri: http://MyPage:8080
          predicates:
            - Path= /myPages/**           

```


## 폴리글랏 퍼시스턴스

CQRS 를 위한 Mypage 서비스만 DB를 구분하여 적용함. 인메모리 DB인 hsqldb 사용.

```
pom.xml 에 적용
<!-- 
		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>runtime</scope>
		</dependency>
 -->
		<dependency>
		    <groupId>org.hsqldb</groupId>
		    <artifactId>hsqldb</artifactId>
		    <version>2.4.0</version>
		    <scope>runtime</scope>
		</dependency>
```


## 동기식 호출과 Fallback 처리

분석단계에서의 조건 중 하나로 예약(Books)->전시(Museums) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 
예약 > 전시 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리
- FeignClient 서비스 구현 

```
# MuseumService.java

@FeignClient(name="Museum", url="${api.museum.url}")//,fallback = MuseumrviceFallback.class)
public interface MuseumService {

    @RequestMapping(method= RequestMethod.PUT, value="/museums/{museumId}", consumes = "application/json")
    public void bookRequest(@PathVariable("museumId") Long museumId, @RequestBody Museum museum);

}
```

- 예약을 전달 받은 직후(@PostPersist) 전시정보를 요청하도록 처리
```
# Book.java

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
    
    
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 전시 시스템이 장애가 나면 예약을 못받는다는 것을 확인


```
#전시(Museum) 서비스를 잠시 내려놓음 (ctrl+c)

#신규 예약 요청
http a8957ed159a9c4f0693659d7848dd3cd-811536622.ap-northeast-1.elb.amazonaws.com:8080/books custNm="MSA" museumId=1 name="A hall" chkDate="20210726"   #Fail


#전시 서비스 재기동
cd Museum
mvn spring-boot:run

#예약 재 처리 
http a8957ed159a9c4f0693659d7848dd3cd-811536622.ap-northeast-1.elb.amazonaws.com:8080/books custNm="MSA" museumId=1 name="A hall" chkDate="20210726"   #Success   

```

- 또한 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다. (서킷브레이커, Fallback 처리는 운영단계에서 설명한다.)



## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트


예약취소가 이루어진 후에 입장승인으로 전달은 동기식이 아니라 비 동기식으로 처리하였다.
 
- 이를 위하여 예약에 기록을 남긴 후에 곧바로 예약취소 되었다는 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
 
```
#Book.java

@Entity
@Table(name="Book_table")
public class Book {

...

    @PostUpdate
    public void onPostUpdate(){

        System.out.println("#### onPostUpdate :" + this.toString());

        if("CANCELED".equals(this.getStatus())) {
            Canceled canceled = new Canceled();
            BeanUtils.copyProperties(this, canceled);
            canceled.publishAfterCommit();
        }
        
```

- 입장승인 서비스에서는 예약취소 이벤트를 수신하고 자신의 정책에 따라 처리하도록 PolicyHandler 를 구현한다

```
package local;

@Service
public class PolicyHandler{

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCanceled_ConfirmCancel(@Payload Canceled canceled){

        if(canceled.isMe()){
            //  예약 취소로 인한 취소
            Confirm temp = confirmRepository.findByBookId(canceled.getId());
            temp.setStatus("CANCELED");
            confirmRepository.save(temp);

        }
    }
 ```
 
입장승인 시스템은 전시/예약과 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 입장승인이 잠시 서비스가 내려간 상태라도 예약 진행은 문제가 없다.

```
#입장승인 서비스를 잠시 내려놓음 (ctrl+c)

#예약 취소 처리 

http PUT a8957ed159a9c4f0693659d7848dd3cd-811536622.ap-northeast-1.elb.amazonaws.com:8080/books custNm="MSA" museumId=1 name="A hall" chkDate="20210726" status="CANCELED"   #Success


#입장승인 확인
http a8957ed159a9c4f0693659d7848dd3cd-811536622.ap-northeast-1.elb.amazonaws.com:8080/confirms      # 입장승인 안바뀜 확인

#입장승인  기동
cd Confirm
mvn spring-boot:run

#입장승인 확인 
http a8957ed159a9c4f0693659d7848dd3cd-811536622.ap-northeast-1.elb.amazonaws.com:8080/confirms      # 입장승인 상태가 "CANCELED" 확인
```



---



# 운영

## CI/CD 설정

각 구현체들은 각자의 source repository 에 구성되었고, 사용한 CI/CD 플랫폼은 AWS CodeBuild를 사용하였으며, 
pipeline build script 는 각 프로젝트 폴더 이하에 buildspec.yml 에 포함되었다.

- CodeBuild 기반으로 CI/CD 파이프라인 구성
MSA 서비스별 CodeBuild 프로젝트 생성하여  CI/CD 파이프라인 구성

![image_repo](https://i.imgur.com/MJwMKq1.png)


- Git Hook 연결
연결한 Github의 소스 변경 발생 시 자동으로 빌드 및 배포 되도록 Git Hook 연결 설정

![image](https://i.imgur.com/LxR4iO4.png)


## 동기식호출 / 서킷브레이킹 / 장애격리

### 서킷 브레이킹 istio-injection + DestinationRule

* istio-injection 적용 (기 적용완료)
```
kubectl label namespace skcc04-ns istio-injection=enabled
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:
- 동시사용자 100명
- 60초 동안 실시

```
$siege -c100 -t60S -r10  -v http://ac8964ea2ef644fb083721500a8e7f07-1903719250.ap-northeast-1.elb.amazonaws.com:8080/cafes 
HTTP/1.1 200     0.15 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.10 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.16 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.11 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.16 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.17 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.16 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.10 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.10 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.12 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.12 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.09 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.11 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.12 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.12 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.10 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.11 secs:     243 bytes ==> GET  /cafes
HTTP/1.1 200     0.11 secs:     243 bytes ==> GET  /cafes
```
```
* 서킷 브레이킹을 위한 DestinationRule 적용
```
```
#dr-cafemanage.yaml  

apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: dr-cafemanage
  namespace: skcc04-ns
spec:
  host: cafemanage
  trafficPolicy:
    connectionPool:
      http:
        http1MaxPendingRequests: 1
        maxRequestsPerConnection: 1
    outlierDetection:
      interval: 1s
      consecutiveErrors: 2
      baseEjectionTime: 10s
      maxEjectionPercent: 100

```
```
$kubectl apply -f dr-cafemanage.yaml

$siege -c100 -t60S -r10  -v http://a753ffdada9fe4c0a8a9d0f885d2b13e-117089432.ap-northeast-1.elb.amazonaws.com:8080/cafes 
HTTP/1.1 200   0.03 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 200   0.04 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 200   0.02 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      81 bytes ==> GET  /cafes
HTTP/1.1 200   0.01 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      81 bytes ==> GET  /cafes
HTTP/1.1 200   0.01 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 200   0.01 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 200   0.01 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 200   0.01 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      81 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      81 bytes ==> GET  /cafes
HTTP/1.1 200   0.02 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 200   0.02 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 200   0.01 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      81 bytes ==> GET  /cafes
HTTP/1.1 503   0.02 secs:      95 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      95 bytes ==> GET  /cafes
HTTP/1.1 200   0.03 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 200   0.02 secs:    5650 bytes ==> GET  /cafes
HTTP/1.1 503   0.00 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.00 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.02 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.02 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.02 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.02 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.02 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.00 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.00 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.02 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.00 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.00 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.00 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.00 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.01 secs:      19 bytes ==> GET  /cafes
HTTP/1.1 503   0.00 secs:      19 bytes ==> GET  /cafes

Transactions:                    194 hits
Availability:                  16.68 %
Elapsed time:                  59.76 secs
Data transferred:               1.06 MB
Response time:                  0.03 secs
Transaction rate:               3.25 trans/sec
Throughput:                     0.02 MB/sec
Concurrency:                    0.10
Successful transactions:         194
Failed transactions:             969
Longest transaction:            0.04
Shortest transaction:           0.00


```

* DestinationRule 적용되어 서킷 브레이킹 동작 확인 (kiali 화면)
![image](https://i.imgur.com/aVctGQF.png)


* 다시 부하 발생하여 DestinationRule 적용 제거하여 정상 처리 확인
```
kubectl delete -f dr-cafemanage.yaml
```


### 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 

* Metric Server 설치(CPU 사용량 체크를 위해)
```
$kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.3.6/components.yaml
$kubectl get deployment metrics-server -n kube-system
```

* (istio injection 적용한 경우) istio injection 적용 해제
```
kubectl label namespace skcc04-ns istio-injection=disabled --overwrite
```

- Deployment 배포시 resource 설정 적용 -> buildsprc.yml 내 resource 설정 적용
```
    spec:
      containers:
          ...
          resources:
            limits:
              cpu: 500m 
            requests:
              cpu: 200m 
```

- replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 10개까지 늘려준다:
```
kubectl autoscale deploy cafe -n skcc04-ns --min=1 --max=10 --cpu-percent=15

# 적용 내용
$kubectl get all -n skcc04-ns

NAME                                    READY   STATUS    RESTARTS   AGE
pod/cafemanage-86f7988679-5bzd2         1/2     Running   2          4m52s
pod/cafemanage-86f7988679-74h7q         1/2     Running   0          111s
pod/cafemanage-86f7988679-d4wfv         1/2     Running   0          95s
pod/cafemanage-86f7988679-hzj2l         1/2     Running   0          2m6s
pod/cafemanage-86f7988679-km27b         1/2     Running   0          2m6s
pod/cafemanage-86f7988679-lrz8p         1/2     Running   0          110s
pod/cafemanage-86f7988679-n9rjn         1/2     Running   0          95s
pod/cafemanage-86f7988679-np8br         1/2     Running   2          11m
pod/cafemanage-86f7988679-pd5gd         1/2     Running   0          111s
pod/cafemanage-86f7988679-zgz7w         1/2     Running   0          110s
pod/gateway-78d565b96c-q74nj            2/2     Running   0          150m
pod/mypage-7b48f44d4-9rxv4              1/2     Running   2          149m
pod/ordermanage-56b566f7df-7m45w        1/2     Running   2          90m
pod/productionmanage-7c44d869f5-p7l5f   1/2     Running   2          149m
pod/siege-95579fcc5-gb7kd               1/1     Running   0          29m

NAME                       TYPE           CLUSTER-IP       EXTERNAL-IP                                                                   PORT(S)          AGE
service/cafemanage         ClusterIP      10.100.192.90    <none>                                                                        8080/TCP         149m
service/gateway            LoadBalancer   10.100.150.152   a8957ed159a9c4f0693659d7848dd3cd-811536622.ap-northeast-1.elb.amazonaws.com   8080:32734/TCP   150m
service/mypage             ClusterIP      10.100.48.244    <none>                                                                        8080/TCP         149m
service/ordermanage        ClusterIP      10.100.211.87    <none>                                                                        8080/TCP         90m
service/productionmanage   ClusterIP      10.100.138.63    <none>                                                                        8080/TCP         149m

NAME                               READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/cafemanage         0/10    10           0           149m
deployment.apps/gateway            1/1     1            1           150m
deployment.apps/mypage             0/1     1            0           149m
deployment.apps/ordermanage        0/1     1            0           90m
deployment.apps/productionmanage   0/1     1            0           149m
deployment.apps/siege              1/1     1            1           29m

NAME                                          DESIRED   CURRENT   READY   AGE
replicaset.apps/cafemanage-86f7988679         10        10        0       149m
replicaset.apps/cafemanage-b6995998c          0         0         0       77m
replicaset.apps/gateway-78d565b96c            1         1         1       150m
replicaset.apps/mypage-7b48f44d4              1         1         0       149m
replicaset.apps/ordermanage-56b566f7df        1         1         0       90m
replicaset.apps/productionmanage-7c44d869f5   1         1         0       149m
replicaset.apps/siege-95579fcc5               1         1         1       29m

NAME                                             REFERENCE               TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
cafemanage   Deployment/cafemanage   131%/15%   1         10        10         65m


```

- siege로 워크로드를 1분 동안 걸어준다.
```
$  siege -c100 -t60S -r10  -v http://a8957ed159a9c4f0693659d7848dd3cd-811536622.ap-northeast-1.elb.amazonaws.com:8080/cafes
```

- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다:
```
kubectl get deploy cafemanage -n skcc04-ns -w 
```

- 스케일 아웃 결과
```
NAME         READY   UP-TO-DATE   AVAILABLE   AGE
cafemanage   1/1     1            1           142m
cafemanage   1/2     1            1           144m
cafemanage   1/2     1            1           144m
cafemanage   1/2     1            1           144m
cafemanage   1/2     2            1           144m
cafemanage   0/2     2            0           145m
cafemanage   0/4     2            0           147m
cafemanage   0/4     2            0           147m
cafemanage   0/4     2            0           147m
cafemanage   0/4     4            0           147m
cafemanage   0/8     4            0           147m
cafemanage   0/8     4            0           147m
cafemanage   0/8     4            0           147m
cafemanage   0/8     8            0           147m
cafemanage   0/10    8            0           147m
cafemanage   0/10    8            0           147m
cafemanage   0/10    8            0           147m
cafemanage   0/10    10           0           147m
```

- kubectl get으로 HPA을 확인하면 CPU 사용률이 131%로 증가됐다.
```
$kubectl get hpa cafemanage -n skcc04-ns 
NAME         REFERENCE               TARGETS    MINPODS   MAXPODS   REPLICAS   AGE
cafemanage   Deployment/cafemanage   131%/15%   1         10        10         65m
```

- siege 의 로그를 보면 Availability가 100%로 유지된 것을 확인 할 수 있다.  
```
Lifting the server siege...
Transactions:                  26446 hits
Availability:                 100.00 %
Elapsed time:                 179.76 secs
Data transferred:               8.73 MB
Response time:                  0.68 secs
Transaction rate:             147.12 trans/sec
Throughput:                     0.05 MB/sec
Concurrency:                   99.60
Successful transactions:       26446
Failed transactions:               0
Longest transaction:            5.85
Shortest transaction:           0.00
```

- HPA 삭제 
```
$kubectl delete hpa cafemanage -n skcc04-ns
```


## 무정지 재배포

먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler, CB 설정을 제거함
Readiness Probe 미설정 시 무정지 재배포 가능여부 확인을 위해 buildspec.yml의 Readiness Probe 설정을 제거함

- seige 및 kiali 화면으로 배포작업 워크로드를 모니터링 함.
![image](https://i.imgur.com/cPzZq15.png)
```
$ siege -v -c1 -t240S --content-type "application/json" 'http://a0a49bc9c3b964b96bf740b592da2520-1468765953.ap-northeast-1.elb.amazonaws.com:8080/cafes POST {"cafeId":"99","cafeNm":"coffee","chkDate":"210713","pcnt":20}'

** SIEGE 4.0.4
** Preparing 100 concurrent users for battle.
The server is now under siege...

HTTP/1.1 200     0.08 secs:       0 bytes ==> POST http://a0a49bc9c3b964b96bf740b592da2520-1468765953.ap-northeast-1.elb.amazonaws.com:8080/cafes {"cafeId":"99","cafeNm":"coffee","chkDate":"210713","pcnt":20}' 
HTTP/1.1 200     0.09 secs:       0 bytes ==> POST http://a0a49bc9c3b964b96bf740b592da2520-1468765953.ap-northeast-1.elb.amazonaws.com:8080/cafes {"cafeId":"99","cafeNm":"coffee","chkDate":"210713","pcnt":20}'
HTTP/1.1 200     0.06 secs:       0 bytes ==> POST http://a0a49bc9c3b964b96bf740b592da2520-1468765953.ap-northeast-1.elb.amazonaws.com:8080/cafes {"cafeId":"99","cafeNm":"coffee","chkDate":"210713","pcnt":20}'
HTTP/1.1 200     0.08 secs:       0 bytes ==> POST http://a0a49bc9c3b964b96bf740b592da2520-1468765953.ap-northeast-1.elb.amazonaws.com:8080/cafes {"cafeId":"99","cafeNm":"coffee","chkDate":"210713","pcnt":20}'
HTTP/1.1 200     0.08 secs:       0 bytes ==> POST http://a0a49bc9c3b964b96bf740b592da2520-1468765953.ap-northeast-1.elb.amazonaws.com:8080/cafes {"cafeId":"99","cafeNm":"coffee","chkDate":"210713","pcnt":20}'
HTTP/1.1 200     0.07 secs:       0 bytes ==> POST http://a0a49bc9c3b964b96bf740b592da2520-1468765953.ap-northeast-1.elb.amazonaws.com:8080/cafes {"cafeId":"99","cafeNm":"coffee","chkDate":"210713","pcnt":20}'

```

- CI/CD 파이프라인을 통해 새버전으로 재배포 작업
Git hook 연동 설정되어 Github의 소스 변경 발생 시 자동 빌드 배포됨
재배포 작업하였으나 Availability 가 떨어지는 현상은 확인하지 못함 (99.97%)


- probe 설정 후, CI/CD 파이프라인을 통해 새버전으로 재배포 작업
```
# buildspec.yaml 의 Readiness probe 의 설정:
- CI/CD 파이프라인을 통해 새버전으로 재배포 작업함

readinessProbe:
    httpGet:
      path: '/actuator/health'
      port: 8080
    initialDelaySeconds: 30
    timeoutSeconds: 2
    periodSeconds: 5
    failureThreshold: 10
    
```

- 동일한 시나리오로 재배포 한 후 Availability 확인:
```
Lifting the server siege...
Transactions:                   3029 hits
Availability:                 100.00 %
Elapsed time:                 239.62 secs
Data transferred:               0.00 MB
Response time:                  0.08 secs
Transaction rate:              12.64 trans/sec
Throughput:                     0.00 MB/sec
Concurrency:                    1.00
Successful transactions:        3029
Failed transactions:               0
Longest transaction:            0.45
Shortest transaction:           0.06

```

배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.


## ConfigMap 사용

시스템별로 또는 운영중에 동적으로 변경 가능성이 있는 설정들을 ConfigMap을 사용하여 관리합니다.
Application에서 특정 도메일 URL을 ConfigMap 으로 설정하여 운영/개발등 목적에 맞게 변경가능합니다.  

* my-config.yaml
```
apiVersion: v1
kind: ConfigMap
metadata:
name: my-config
namespace: skcc04-ns
data:
api.cafe.url: http://CafeManage:8080

```
buildspec.yaml에 my-config라는 CongifMap을 생성하고, key 값에 도메인 url을 등록한다. 

* OrderManage/buildsepc.yaml (configmap 사용)
```
        ...
        cat <<EOF | kubectl apply -f -
        apiVersion: v1
        kind: ConfigMap
        metadata:
          name: my-config
          namespace: $_NAMESPACE
        data:
          api.cafe.url: http://CafeManage:8080
        EOF
      - |
        cat  <<EOF | kubectl apply -f -
        apiVersion: apps/v1
        kind: Deployment
        metadata:
          name: $_PROJECT_NAME
          namespace: $_NAMESPACE
          labels:
            app: $_PROJECT_NAME
        spec:
          replicas: 1
          selector:
            matchLabels:
              app: $_PROJECT_NAME
          template:
            metadata:
              labels:
                app: $_PROJECT_NAME
            spec:
              containers:
                - name: $_PROJECT_NAME
                  image: $AWS_ACCOUNT_ID.dkr.ecr.$_AWS_REGION.amazonaws.com/$_ECR_NAME-$_PROJECT_NAME:latest
                  ports:
                    - containerPort: 8080
                  env:
                    - name: api.cafe.url
                      valueFrom:
                        configMapKeyRef:
                          name: my-config
                          key: api.cafe.url
                  imagePullPolicy: Always
                  ...
```
Deployment yaml에 해당 configMap 적용

* CafeService.java
```
@FeignClient(name="CafeManage", url="${api.cafe.url}")//,fallback = CafeServiceFallback.class)
public interface CafeService {

    @RequestMapping(method= RequestMethod.PUT, value="/cafes/{cafeId}", consumes = "application/json")
    public void orderRequest(@PathVariable("cafeId") Long cafeId, @RequestBody Cafe cafe);

}
```
url에 configMap 적용

* kubectl describe pod ordermanage-74c7d468df -n skcc04-ns
```
Containers:
  ordermanage:
    Container ID:   docker://c2bf8e39767b9f6767e80d31642cac85ad9df6ff2f40ea84948ef092a1c99c7d
    Image:          879772956301.dkr.ecr.ap-northeast-1.amazonaws.com/user04-ordermanage:latest
    Image ID:       docker-pullable://879772956301.dkr.ecr.ap-northeast-1.amazonaws.com/user04-ordermanage@sha256:d09e8bd588423c698e0466e90263c8ba43770472425ebd562c589fcd9ddf84a7
    Port:           8080/TCP
    Host Port:      0/TCP
    State:          Running
      Started:      Tue, 13 Jul 2021 03:42:21 +0000
    Ready:          True
    Restart Count:  0
    Liveness:       http-get http://:15020/app-health/ordermanage/livez delay=150s timeout=2s period=5s #success=1 #failure=5
    Readiness:      http-get http://:15020/app-health/ordermanage/readyz delay=30s timeout=2s period=5s #success=1 #failure=10
    Environment:
      api.cafe.url:  <set to the key 'api.cafe.url' of config map 'my-config'>  Optional: false
    Mounts:
      /var/run/secrets/kubernetes.io/serviceaccount from default-token-j7j95 (ro)
```
kubectl describe 명령으로 컨테이너에 configMap 적용여부를 알 수 있다. 

