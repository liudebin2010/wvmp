# wvp-2.0
在1.0的基础上实现国标级联

WEB VIDEO PLATFORM是一个基于GB28181-2016标准实现的网络视频平台，负责实现核心信令与设备管理后台部分，支持NAT穿透，支持海康、大华、宇视等品牌的IPC、NVR、DVR接入。   
流媒体服务基于ZLMediaKit-https://github.com/xiongziliang/ZLMediaKit
前段页面基于MediaServerUI进行修改.

# 应用场景：
支持摄像机、平台、NVR等设备接入.
# 项目目标
旨在打造一个易配置,易使用,便于维护的28181国标信令系统, 依托优秀的开源流媒体服务框架ZLMediaKit, 实现一个完整易用GB28181平台. 

# gitee同步仓库
https://gitee.com/18010473990/wvp-GB28181.git


# 1.0 支持特性  
1. 视频预览;  
2. 云台控制（方向、缩放控制）;  
3. 视频设备信息同步;   
4. 离在线监控;  
5. 录像查询与回放（基于NVR\DVR，暂不支持快进、seek操作）;  
6. 无人观看自动断流;    
7. 支持UDP和TCP两种国标信令传输模式; 
8. 集成web界面, 不需要单独部署前端服务, 直接利用wvp内置文件服务部署, 随wvp一起部署;   
9. 支持平台接入, 针对大平台大量设备的情况进行优化;  
10. 支持检索,通道筛选;  
11. 支持自动配置ZLM媒体服务, 减少因配置问题所出现的问题;  
12. 支持启用udp多端口模式, 提高udp模式下媒体传输性能;  
13. 支持通道是否含有音频的设置;  
14. 支持通道子目录查询;  
15. 支持udp/tcp国标流传输模式;  
16. 支持直接输出RTSP、RTMP、HTTP-FLV、Websocket-FLV、HLS多种协议流地址  
17. 支持国标网络校时  
18. 支持公网部署, 支持wvp与zlm分开部署   
19. 支持播放h265, g.711格式的流(需要将closeWaitRTPInfo设为false). 

# 2.0 支持特性 
- [ ] 国标通道向上级联  
    - [X] WEB添加上级平台
    - [X] 注册
    - [X] 心跳保活
    - [X] 通道选择
    - [ ] 通道推送
    - [ ] 点播
    - [ ] 云台控制
- [ ] 添加RTSP视频  
- [ ] 添加ONVIF探测局域网内的设备  
- [ ] 添加RTMP视频  
- [ ] 添加系统配置  
- [ ] 添加用户管理  
 
 

# 项目部署
参考:[WIKI](https://github.com/648540858/wvp-GB28181-pro/wiki)

# gitee同步仓库
https://gitee.com/18010473990/wvp-GB28181.git  

# 使用帮助
QQ群: 901799015, 542509000(ZLM大群)  
QQ私信一般不回, 精力有限.欢迎大家在群里讨论.

# 致谢
感谢作者[夏楚](https://github.com/xiongziliang) 提供这么棒的开源流媒体服务框架  

