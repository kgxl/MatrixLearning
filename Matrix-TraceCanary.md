## Matrix-TraceCanary

[toc]

前提

* 什么是卡顿

  ​		由于人类眼睛的特殊生理结构，如果所看画面之帧率高于每秒约10至12帧的时候，就会认为是连贯的，此现象称之为视觉暂留，一般电视或电影基本都是25~30FPS，**FPS 低并不意味着卡顿发生，而卡顿发生 FPS 一定不高**，所以另一个衡量卡顿的就是掉帧程度。Android中正常来说是60帧为标准，也就是一帧的准备时间为1000/60=16.6667ms，当一帧准备时间为32ms时，FPS为30帧，这时肉眼还不会发现卡顿，一旦发生了某次帧率很低时就容易感知到。

### 统计核心类

* UIThreadMonitor

  对Choreographer中的```addCallbackLocked```方法进行反射，用于发送```CALLBACK_INPUT```、```CALLBACK_ANIMATION```、```CALLBACK_TRAVERSAL```监听。

* LooperMonitor

  对Handler中```mLogging```设置自定义的```Printer```监听每一条Message。

### EvilMethodTracer


### StartupTracer

### FrameTracer

### AnrTracer

### 统计插桩方法

使用gradle plugin ASM插桩在所有的方法都插入AppMethodBeat.i()与AppMethodBeat.o()统计方法耗时

