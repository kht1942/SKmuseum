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

![](https://i.imgur.com/ywqKPKW.png)


2. 사용자가 전시회 관람을 예약한다.

![](https://i.imgur.com/ivp44ru.png)


3. 사용자의 예약에 의해 해당 전시의 참석가능인원 수가 감소한다.(Sync)

![](https://i.imgur.com/GZsBAm4.png)


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
* 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: 이때 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 쉬운 일상 용어로 사용했다.

![](https://i.imgur.com/OebnhHI.png)

![](https://i.imgur.com/PBBNEfH.png)


* Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다

![](https://i.imgur.com/UWdzLXS.png)


* MyPage 는 CQRS 를 위한 서비스

| MSA | 기능 | port | 조회 API | Gateway 사용시 |
|---|:---:|:---:|---|---|
| Book | 예약 관리 | 8081 | http://localhost:8081/books | http://Book:8080/books |
| Museum  | 전시 관리 | 8082 | http://localhost:8082/museums | http://Museum:8080/museums |
| Confirm | 입장승인 관리 | 8083 | http://localhost:8083/confirms | http://Confirm:8080/confirms |
| MyPage | myPage | 8084 | http://localhost:8084/myPages | http://MyPage:8080/myPages |


## Gateway 적용
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트와 파이선으로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 808n 이다)

![](https://i.imgur.com/JTrvLan.png)

![](https://i.imgur.com/KktFEGh.png)



## 폴리글랏 퍼시스턴스

CQRS 를 위한 Mypage 서비스만 DB를 구분하여 적용함. 인메모리 DB인 hsqldb 사용.


![](https://i.imgur.com/6dUyZWy.png)





## 동기식 호출과 Fallback 처리

분석단계에서의 조건 중 하나로 예약(Books)->전시(Museums) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 
예약 > 전시 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리

- FeignClient 서비스 구현 

![](https://i.imgur.com/mDDOj1v.png)




- 예약을 전달 받은 직후(@PostPersist) 전시정보를 요청하도록 처리

![](https://i.imgur.com/IBdNhZN.png)


- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 전시 시스템이 장애가 나면 예약을 못받는다는 것을 확인


- 전시 목록

![](https://i.imgur.com/pkAUeQE.png)

- 전시(Museum) 서비스를 잠시 내려놓음

![](https://i.imgur.com/vrlmddp.png)

- 예약 요청(실패)

![](https://i.imgur.com/DqTiAHB.png)


- 전시 서비스 재기동

![](https://i.imgur.com/roBbryc.png)


- 예약 처리(성공)

![](https://i.imgur.com/YXHep3W.png)


- 또한 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다. (서킷브레이커, Fallback 처리는 운영단계에서 설명한다.)



## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트


예약취소가 이루어진 후에 입장승인으로 전달은 동기식이 아니라 비 동기식으로 처리하였다.
 
- 이를 위하여 예약에 기록을 남긴 후에 곧바로 예약취소 되었다는 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
 
![](https://i.imgur.com/UoCsvuk.png)




- 입장승인 서비스에서는 예약취소 이벤트를 수신하고 자신의 정책에 따라 처리하도록 PolicyHandler 를 구현한다

![](https://i.imgur.com/cnoquwt.png)


 
입장승인 시스템은 전시/예약과 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 입장승인이 잠시 서비스가 내려간 상태라도 예약 진행은 문제가 없다.


- 입장승인 서비스를 잠시 내려놓음

![](https://i.imgur.com/7BbzLGi.png)


- 예약 등록 취소

![](https://i.imgur.com/KbmJImg.png)



- 입장승인 확인 불가

![](https://i.imgur.com/LKdEsd5.png)


- 입장승인  기동

![](https://i.imgur.com/ZVJBAG5.png)


- 예약 취소 확인

![](https://i.imgur.com/8BMf52d.png)



---



# 운영

## CI/CD 설정

각 구현체들은 각자의 source repository 에 구성되었고, 사용한 CI/CD 플랫폼은 AWS CodeBuild를 사용하였으며, 
pipeline build script 는 각 프로젝트 폴더 이하에 buildspec.yml 에 포함되었다.

- CodeBuild 기반으로 CI/CD 파이프라인 구성
MSA 서비스별 CodeBuild 프로젝트 생성하여  CI/CD 파이프라인 구성

![](https://i.imgur.com/ltdmoqB.png)

![](https://i.imgur.com/YSzg7zi.png)


- Git Hook 연결
연결한 Github의 소스 변경 발생 시 자동으로 빌드 및 배포 되도록 Git Hook 연결 설정

![](https://i.imgur.com/IlVjIeT.png)


- Pipeline 결과
CodeBuild 가 자동 실행 되어 EKS 클러스터에 배포 후 서비스가 정상적으로 기동 되었는지 확인한다

![](https://i.imgur.com/wTvk2MW.png)




## ConfigMap 사용

시스템별로 또는 운영중에 동적으로 변경 가능성이 있는 설정들을 ConfigMap을 사용하여 관리합니다.
Application에서 특정 도메일 URL을 ConfigMap 으로 설정하여 운영/개발등 목적에 맞게 변경가능합니다.  

* my-config.yaml

![](https://i.imgur.com/4ArtNCx.png)




buildspec.yaml에 my-config라는 CongifMap을 생성하고, key 값에 도메인 url을 등록한다. 

* buildsepc.yaml (configmap 사용)

![](https://i.imgur.com/pst107P.png)

![](https://i.imgur.com/NN9c6z9.png)



Deployment yaml에 해당 configMap 적용

* MuseumService.java

![](https://i.imgur.com/bfTAZG8.png)


url에 configMap 적용
![](https://i.imgur.com/tbBCLWY.png)


kubectl describe 명령으로 컨테이너에 configMap 적용여부를 알 수 있다. 


## 동기식호출 / 서킷브레이킹 / 장애격리(실패)

### 서킷 브레이킹 istio-injection + DestinationRule



