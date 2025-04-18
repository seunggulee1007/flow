# 대기열 시스템

티켓팅, 수강신청과 같은 특정 시간에 요청이 많이 들어올 경우, 특정 시간 때문에 리소스를 늘리는 것 보다, 대기열을 통해 해당 시스템의 허용 범위를 지정해 주는 것이 좋다.
해당 프로젝트에서는 대기열을 만들고 해당 API 의 성능이 어느정도인지 측정해 보도록 한다.

# JMeter
jmeter를 이용해 성능을 측정하고, 유저는 30명, 전체 쓰레드가 사용되는 시간은 10초로 설정.

## 설정
![image](https://github.com/user-attachments/assets/3da0beb5-efef-4744-a308-b04369d3ade8)

## 결과 요약
![image](https://github.com/user-attachments/assets/f5a409b5-1895-41dc-9f39-eec6b9eddaf5)
## Response times Over Time
![image](https://github.com/user-attachments/assets/80f29175-a3d9-45aa-bb13-738e1f353cb8)
## Transaction per second
![image](https://github.com/user-attachments/assets/0557f8cf-da08-4791-9e33-57082863c912)

## Response Codes per Second
![image](https://github.com/user-attachments/assets/9c897e0f-2c8a-4096-8eb2-c0d8afc0180a)

## Response Latencies Over Time
![image](https://github.com/user-attachments/assets/c530406d-f939-4f3e-83b7-6b69f71a4fee)


