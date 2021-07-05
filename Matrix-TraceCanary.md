## Matrix-TraceCanary

[toc]

前提

* 什么是卡顿

  ​		由于人类眼睛的特殊生理结构，如果所看画面之帧率高于每秒约10至12帧的时候，就会认为是连贯的，此现象称之为视觉暂留，一般电视或电影基本都是25~30FPS，**FPS 低并不意味着卡顿发生，而卡顿发生 FPS 一定不高**，所以另一个衡量卡顿的就是掉帧程度。Android中正常来说是60帧为标准，也就是一帧的准备时间为1000/60=16.6667ms，当一帧准备时间为32ms时，FPS为30帧，这时肉眼还不会发现卡顿，一旦发生了某次帧率很低时就容易感知到。

### 核心类

* UIThreadMonitor

  对Choreographer中的```addCallbackLocked```方法进行反射，用于发送```CALLBACK_INPUT```、```CALLBACK_ANIMATION```、```CALLBACK_TRAVERSAL```监听。

* LooperMonitor

  对Handler中```mLogging```设置自定义的```Printer```监听每一条Message。

### AnrTracer

​	**作用**

​	用于检测主线程的ANR并尝试提供准确的可供分析定位的快照，特别适用于不可复现的卡顿场景（可复现的场景可以通过Trace来得到函数调用栈的快照以进一步分析）。

​	**原理**

​	设置```Looper#mLogging```监听每条message，如果是```>>>>> Dispatching to ```开头为开始分发，```<<<<< Finished to```开头为分发结束，根据这两个之间时间差值为判断是否发生ANR。

​	**实现**

```java
    @Override
    public void dispatchBegin(long beginNs, long cpuBeginMs, long token) {
        super.dispatchBegin(beginNs, cpuBeginMs, token);

        anrTask.beginRecord = AppMethodBeat.getInstance().maskIndex("AnrTracer#dispatchBegin");
        anrTask.token = token;

        if (traceConfig.isDevEnv()) {
            MatrixLog.v(TAG, "* [dispatchBegin] token:%s index:%s", token, anrTask.beginRecord.index);
        }
        long cost = (System.nanoTime() - token) / Constants.TIME_MILLIS_TO_NANO;
        //每个消息开始分发的时候发送一个（Constants.DEFAULT_ANR - cost）延时的消息
        //一旦超过这个延时时间，执行anrTask，说明发生了ANR，然后dump出堆栈信息
        anrHandler.postDelayed(anrTask, Constants.DEFAULT_ANR - cost);
        lagHandler.postDelayed(lagTask, Constants.DEFAULT_NORMAL_LAG - cost);
    }


    @Override
    public void dispatchEnd(long beginNs, long cpuBeginMs, long endNs, long cpuEndMs, long token, boolean isBelongFrame) {
        super.dispatchEnd(beginNs, cpuBeginMs, endNs, cpuEndMs, token, isBelongFrame);
        if (traceConfig.isDevEnv()) {
            long cost = (endNs - beginNs) / Constants.TIME_MILLIS_TO_NANO;
            MatrixLog.v(TAG, "[dispatchEnd] token:%s cost:%sms cpu:%sms usage:%s",
                    token, cost, cpuEndMs - cpuBeginMs, Utils.calculateCpuUsage(cpuEndMs - cpuBeginMs, cost));
        }
        if (null != anrTask) {
             //结束消息删除task
            anrTask.getBeginRecord().release();
            anrHandler.removeCallbacks(anrTask);
        }
        if (null != lagTask) {
            lagHandler.removeCallbacks(lagTask);
        }
    }

```



### EvilMethodTracer

​	**作用**

​	主线程执行时耗时过长的方法，可以看作AnrTracer的补充，提供了一个更细的分析粒度。

​	**原理**

​	与anrTrace一样，只是会在gradle打包时ASM会插桩统计每个方法耗时，在发现耗时操作时使用AppMethodBeat取出该期间在主线程中所有执行过的方法记录。

### StartupTracer

​	**作用**

​		用于监控冷/热启动场景下的性能问题，提供的分析快照包含冷热启动的总耗时、application耗时、启动页耗时、以及相应期间的函数调用栈及其耗时。

​	**原理**

```
firstMethod.i       LAUNCH_ACTIVITY   onWindowFocusChange   LAUNCH_ACTIVITY    onWindowFocusChange
^                         ^                   ^                     ^                  ^
|                         |                   |                     |                  |
|---------app---------|---|---firstActivity---|---------...---------|---careActivity---|
|<--applicationCost-->|
|<--------------firstScreenCost-------------->|
|<---------------------------------------coldCost------------------------------------->|
.                         |<-----warmCost---->|
```

​	**实现**

* Application初始化耗时（applicationCost）：
  * 起始点：通过初始化App时反射ActivityThread时开始计时
  * 结束点：在接收到```LAUNCH_ACTIVITY```时作为application创建结束时间。

- 首屏耗时(firstScreenCost)：
  - 指app启动到第一个Activity初始化完成的耗时，可以认为包含了applicationCost +第一个运行activity的初始化耗时。
  - 起始点和applicationCost的起始点一样。
  - 结束点对应第一个运行activity的`onWindowFocusChange()`，在`StartupTracer.onActivityFocused()`中标记。`Activity.onWindowFocusChange()`执行时，认为Activity对用户可见。

- 冷启动（coldCost）
  - app启动到第一个对用户有意义的Activity（对应图中的careActivity）初始化完成耗时。
  - 应用一般仅在闪屏页即launch activity做logo展示、应用初始化的工作，其后的第一个Activity做为主页Activity，这个Activity就是careActivity。所以把careActivity的初始化完成做为coldCost的结束点。
- 快启动耗时(warmCost)
  - 因为Application不会重新初始化，所以只统计Activity的初始化耗时，和firstActivity含义一致。
  - 起始点是launchActivity初始化。
  - 结束点是launchActivity ```onWindfocusChanged()```。

### FrameTracer

​	**作用**

​		每个页面在可见时可知帧率，掉帧情况，内部包含一个```FrameDecorator```用于展示帧率情况。

​	**原理**

​		通过反射Choreographer中[addCallbackLocked](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/view/Choreographer.java;drc=master;bpv=1;bpt=1;l=1005?q=choreographer&ss=android%2Fplatform%2Fsuperproject)方法在 [mCallbackQueues](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/view/Choreographer.java;drc=master;bpv=1;bpt=1;l=174?q=choreographer&ss=android%2Fplatform%2Fsuperproject)头部插入对应三种类型callback。在下一个callback执行时，计算两种callback时间差。开始时间为```mTimestampNanos```，这个变量是由当前帧开始时由cpp底层发送到Choreographer的onVsync方法中。

![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/e23c5232b56a4eb0a19bd197665e8bee~tplv-k3u1fbpfcp-watermark.image)

### 统计插桩方法

使用gradle plugin ASM插桩在所有的方法执行前插入AppMethodBeat.i()与方法最后插入AppMethodBeat.o()统计方法耗时。

