## Matrix-IOCanary

## Closeable Leak监控

* 前提

  能检测到IO流，Cursor等未执行close引起的泄漏。

* 原理

  Android原生自带StrictMode自带检测Closeable Leak。使用```dalvik.system.CloseGuard```类监控。

  ```java
   * class Bar {
   *
   *       {@literal @}ReachabilitySensitive
   *       private final CloseGuard guard = CloseGuard.get();
   *
   *       ...
   *
   *       public Bar() {
   *           ...;
   *       }
   *
   *       public void connect() {
   *          ...;
   *    		//开始监听
   *          guard.open("cleanup");
   *       }
   *
   *       public void cleanup() {
   *			//结束监听
   *          guard.close();
   *          ...;
   *       }
   *
   *       protected void finalize() throws Throwable {
   *           try {
   *               // Note that guard could be null if the constructor threw.
   *               if (guard != null) {
   *					//查看是否关闭
   *                   guard.warnIfOpen();
   *               }
   *               cleanup();
   *           } finally {
   *               super.finalize();
   *           }
   *       }
   *   }
   * }
  ```

  每次在初始化的时候都执行```guard.open```开始监控，当执行close时执行```guard.close```，当下一次GC执行此类的Finalize时，会去判断其中的```closerNameOrAllocationInfo```是否为空，然后通过```Reporter```返回结果。以此得知是Closeable Leak。

* 实现

  由原理可知，```StrictMode```中使用**```dalvik.system.CloseGuard```**来做监控，**```Reporter```**做消息分发。所以hook两点：

  ```
  1.通过反射开启监控功能
  2.动态代理替换Reporter，替换为自己的Reporter
  ```

  具体代码如下：

  ```java
  public static void hookStrictMode() {
          try {
              Class<?> GuardClass = Class.forName("dalvik.system.CloseGuard");
              Class<?> ReportClass = Class.forName("dalvik.system.CloseGuard$Reporter");
              Method setReporter = GuardClass.getDeclaredMethod("setReporter", ReportClass);
              Method setEnabled = GuardClass.getDeclaredMethod("setEnabled", boolean.class);
  
              @SuppressLint("SoonBlockedPrivateApi")
              //此处为黑名单方法无法反射，所以需要使用android P以上反射方法
              Method getReporter = GuardClass.getDeclaredMethod("getReporter");
              setEnabled.invoke(null, true);
              Object originReporter = getReporter.invoke(null);
              //动态代理替换Reporter
              setReporter.invoke(null, Proxy.newProxyInstance(ReportClass.getClassLoader(), new Class[]{ReportClass}, new InvocationHandler() {
                  @Override
                  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                      System.out.println("method = " + method.getName());
                      if (method.getName().equals("report")) {
                          if (args.length != 2) {
                              return null;
                          }
                          if (!(args[1] instanceof Throwable)) {
                              return null;
                          }
                          Throwable throwable = (Throwable) args[1];
                          throwable.printStackTrace();
                          return null;
                      }
                      return method.invoke(originReporter, args);
                  }
              }));
  
          } catch (ClassNotFoundException e) {
              e.printStackTrace();
          } catch (NoSuchMethodException e) {
              e.printStackTrace();
          } catch (IllegalAccessException e) {
              e.printStackTrace();
          } catch (InvocationTargetException e) {
              e.printStackTrace();
          }
      }
  ```

  反射Android P hide方法：

  ```
   public static void hookP() {
          //利用元反射与豁免api取消黑名单限制
          //所谓元反射就是 套娃，通过自身方法反射自己
          try {
              Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
              Method forName = Class.class.getDeclaredMethod("forName", String.class);
              Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
              Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
              Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
              Object vm = getRuntime.invoke(null);
              //因为底层c++使用的签名匹配，Java中类名全部为L开头，由此绕过api限制
              setHiddenApiExemptions.invoke(vm,new Object[]{"L"});
          } catch (NoSuchMethodException e) {
              e.printStackTrace();
          } catch (IllegalAccessException e) {
              e.printStackTrace();
          } catch (InvocationTargetException e) {
              e.printStackTrace();
          }
      }
  ```

  至此，每当有没有关闭close时，都会report。

