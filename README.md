## About okHttp	
	val okHttpClient:OkHttpClient = OkHttpClient.Builder().build()
	val mediaType: MediaType? = MediaType.parse("application/json; charset=utf-8")
    val json:String = "{\n" +
            "\"books\":\"\",\n" +
            "\"num\":10,\n" +
            "\"page\":1\n" +
            "}"
	val requestBody:RequestBody = RequestBody.create(mediaType,json)
    val request:Request = Request.Builder()
            .url("*******")
            .post(requestBody)
            .build()
   	okHttpClient.newCall(request).enqueue(this)
	...
	override fun onFailure(call: Call, e: IOException) {
        runOnUiThread { runOnUiThread({
            content.text = e.message.toString()
        }) }
    }

    override fun onResponse(call: Call, response: Response) {
        runOnUiThread { content.text = response.body()?.string() }
    }

---
* 1
	* 1.1 
	
			val okHttpClient:OkHttpClient = OkHttpClient.Builder().build()
			// 明显的不能再明显，Builder设计模式，让我们来看看在这个OkHttpClient中大致可以封装哪些参数吧。
			$OkHttpClient.java
			OkhttpClient{
				...
			static class Builder{
			//拦截器也是应该重点分析的一个点
		    final List<Interceptor> interceptors = new ArrayList<>();
		    final List<Interceptor> networkInterceptors = new ArrayList<>();

			  public Builder() {
      			dispatcher = new Dispatcher(); //创建了这个dispatcher，我们来看下这个dispatcher
				...
 				connectionPool = new ConnectionPool(); //连接池，这里面也有故事
				...
			 	retryOnConnectionFailure = true; //失败,默认 重连
			    callTimeout = 0;			//整个请求的时间范围
			    connectTimeout = 10_000;
			    readTimeout = 10_000;
			    writeTimeout = 10_000;
			    pingInterval = 0;
				}
			}

			}
			$ Dispatcher.java
			/**
			 * Policy on when async requests are executed.  执行异步请求
			 */
			public final class Dispatcher {
			private int maxRequests = 64; //最大请求数 64 
			private int maxRequestsPerHost = 5;	//姑且猜测这个参数是每个ULI重试请求的最大次数 5 次
			private @Nullable Runnable idleCallback;
			
			/** Executes calls. Created lazily. */
			private @Nullable ExecutorService executorService; //看到了最熟悉的陌生人：可爱的线程池
			
			/** Ready async calls in the order they'll be run. *//数组队列存放的是AsyncCall（异步call）
			private final Deque<AsyncCall> readyAsyncCalls = new ArrayDeque<>(); //准备运行
			/** Running asynchronous calls. Includes canceled calls that haven't finished yet. */
			private final Deque<AsyncCall> runningAsyncCalls = new ArrayDeque<>(); //还没运行完成的AsyncCall
			
			/** Running synchronous calls. Includes canceled calls that haven't finished yet. */
			//上面有一个 AsyncCall ，这出现了一个RealCall
			private final Deque<RealCall> runningSyncCalls = new ArrayDeque<>();
			
			public Dispatcher(ExecutorService executorService) { //调用这个构造函数也就意味着你可以自己定义你自己的线程池
			    this.executorService = executorService;
			  }
			
			public Dispatcher() { // OkhttpClient中默认调用的是这个空参的构造函数
			  }
			
			public synchronized ExecutorService executorService() {
		    if (executorService == null) {
			//默认的线程池，coolPoosize的值是0，MaxminPoolSize 是无限大，超时时间60秒。也就是说你来多少请求，我就开多少个线程去处理。
		      executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
		          new SynchronousQueue<>(), Util.threadFactory("OkHttp Dispatcher", false));
		    }
		    return executorService;
		 	 }

			$ ConnectionPool.java
			public final class ConnectionPool {
			  final RealConnectionPool delegate;
			
			  /**
			   * Create a new connection pool with tuning parameters appropriate for a single-user application.
			   * The tuning parameters in this pool are subject to change in future OkHttp releases. Currently
			   * this pool holds up to 5 idle connections which will be evicted after 5 minutes of inactivity.
			   */
			  public ConnectionPool() {
			    this(5, 5, TimeUnit.MINUTES); //解释的也很清楚，5个空闲，可以有5分钟空闲时间
			  }
			
			  public ConnectionPool(int maxIdleConnections, long keepAliveDuration, TimeUnit timeUnit) {
				//真正搞事情的是RealConnectPool,有点像那种WindowMangerImp里面又使用WinMangerGlobal来搞事情
			    this.delegate = new RealConnectionPool(maxIdleConnections, keepAliveDuration, timeUnit);
			  }
			
			  /** Returns the number of idle connections in the pool. */
			  public int idleConnectionCount() {
			    return delegate.idleConnectionCount();
			  }
			
			  /** Returns total number of connections in the pool. */
			  public int connectionCount() {
			    return delegate.connectionCount();
			  }
			
			  /** Close and remove all idle connections in the pool. */
			  public void evictAll() {
			    delegate.evictAll();
			  }
			}

			$ RealConnectionPool.java
			
			public final class RealConnectionPool {
			  /**
			   * Background threads are used to cleanup expired connections. There will be at most a single
			   * thread running per connection pool. The thread pool executor permits the pool itself to be
			   * garbage collected.
			   */
			// 又是一个线程池，目前为止我们已经看到了两个线程池，另一个在Dispatcher中。执行的时候，肯定会走这些线程池，所以，暂时放一下，我们一会儿再来仔细的分析。
			  private static final Executor executor = new ThreadPoolExecutor(0 /* corePoolSize */,
			      Integer.MAX_VALUE /* maximumPoolSize */, 60L /* keepAliveTime */, TimeUnit.SECONDS,
			      new SynchronousQueue<>(), Util.threadFactory("OkHttp ConnectionPool", true));

			
			当调用build()的时候，就直接new OkHttpClient(this); 完成了参数封装。
			至此OkHttpClient中差不多了，我们接下来看下请求参数那一块的。

* 2
	* 2.1
			
			//如何构建请求体？
			//请求体分为哪些请求体？
			//究竟是人性的扭曲，还是道德的沦丧，为何隔壁村频频失盗？
			val requestBody:RequestBody = RequestBody.create(mediaType,json)

			  /**
			   * Returns a new request body that transmits {@code content}. If {@code contentType} is non-null
			   * and lacks a charset, this will use UTF-8.
			   */
			  public static RequestBody create(@Nullable MediaType contentType, String content) {
			    Charset charset = UTF_8;
			    if (contentType != null) {
			      charset = contentType.charset();
			      if (charset == null) {
			        charset = UTF_8; //Utf-8编码
			        contentType = MediaType.parse(contentType + "; charset=utf-8");
			      }
			    }
			    byte[] bytes = content.getBytes(charset);
			    return create(contentType, bytes);
			  }

			  /** Returns a new request body that transmits {@code content}. */
			  public static RequestBody create(final @Nullable MediaType contentType, final byte[] content,
			      final int offset, final int byteCount) {
			    if (content == null) throw new NullPointerException("content == null");
			    Util.checkOffsetAndCount(content.length, offset, byteCount);
			    return new RequestBody() { 					//自己new了一个RequestBody出来
			      @Override public @Nullable MediaType contentType() {
			        return contentType; 					//返回类型
			      }
			
			      @Override public long contentLength() {
			        return byteCount;						//返回长度
			      }
			
			      @Override public void writeTo(BufferedSink sink) throws IOException {
			        sink.write(content, offset, byteCount);  //写入
			      }
			    };
			  }
			
			  /** Returns a new request body that transmits the content of {@code file}. */
			  public static RequestBody create(final @Nullable MediaType contentType, final File file) {
			    if (file == null) throw new NullPointerException("file == null"); //针对上传文件
			
			    return new RequestBody() {
			      @Override public @Nullable MediaType contentType() {
			        return contentType;
			      }
			
			      @Override public long contentLength() {
			        return file.length();
			      }
			
			      @Override public void writeTo(BufferedSink sink) throws IOException {
			        try (Source source = Okio.source(file)) { // okio
			          sink.writeAll(source);
			        }
			      }
			    };
			  }
		比较常用的：FormBody、MultipartBody
		
	* 2.2
	
			$ FormBody.java 表单提交，实现了ReuqestBody 这个抽象类。也采用Builder设计模式，也就是使用方式是
			val:requstBody = RequestBody.Builder()
					.add("key1","value1")
					.add("key2","value2")
					...
					build();
			看FormBody里面的操作是：
			    for (int i = 0, size = encodedNames.size(); i < size; i++) {
			      if (i > 0) buffer.writeByte('&');
			      buffer.writeUtf8(encodedNames.get(i));
			      buffer.writeByte('=');
			      buffer.writeUtf8(encodedValues.get(i));
			    } // 即 key1=value1&key2=value2&key3=value3... 形成这样的请求体。
			
			    if (countBytes) {
			      byteCount = buffer.size();
			      buffer.clear();
			    }
			
			    return byteCount;
			  } 
	* 2.3
		
			// 开始请求吧，just do it
			okHttpClient.newCall(request).enqueue(this)
			$ OkHttpClient.java 
			@Override public Call newCall(Request request) { // 可见 交给RealCall去处理
				// 第一个参数 this 把自己传给了 RealCall
			    return RealCall.newRealCall(this, request, false  /* for web socket 最后一个参数验证是否是webSocket*/);
			  }

			$ ReallCall.java
			  @Override public void enqueue(Callback responseCallback) {
			    synchronized (this) { //对象锁 ，当前的call是否在执行。如果你重复执行那么抛出异常
			      if (executed) throw new IllegalStateException("Already Executed");
			      executed = true; 
			    }
			    transmitter.callStart();
				//调用 $Dispatcher.java 的enqueue(AsyncCall asyncCall)
				// 带上AsyncCall 进入到DisPatcher中
			    client.dispatcher().enqueue(new AsyncCall(responseCallback));//这个AsyncCall是个什么鬼？让我们来一探究竟
			  }
			$ AsyncCall.java 一看原来是RealCall中的内部类
			  final class AsyncCall extends NamedRunnable { //继承自这个NamedRunnable的Runnable接口
			    private final Callback responseCallback;
			    private volatile AtomicInteger callsPerHost = new AtomicInteger(0);
			
			    AsyncCall(Callback responseCallback) {
			      super("OkHttp %s", redactedUrl());
			      this.responseCallback = responseCallback;
			    }
			
			    AtomicInteger callsPerHost() {
			      return callsPerHost;
			    }
			
			    void reuseCallsPerHostFrom(AsyncCall other) {
			      this.callsPerHost = other.callsPerHost;
			    }
			
			    String host() {
			      return originalRequest.url().host();
			    }
			
			    Request request() {
			      return originalRequest;
			    }
			
			    RealCall get() {
			      return RealCall.this;
			    }

		    /**
		     * Attempt to enqueue this async call on {@code executorService}. This will attempt to clean up
		     * if the executor has been shut down by reporting the call as failed.
		     */
		    void executeOn(ExecutorService executorService) {
		      assert (!Thread.holdsLock(client.dispatcher()));
		      boolean success = false;
		      try {
		        executorService.execute(this);
		        success = true;
		      } catch (RejectedExecutionException e) {
		        InterruptedIOException ioException = new InterruptedIOException("executor rejected");
		        ioException.initCause(e);
		        transmitter.noMoreExchanges(ioException);
		        responseCallback.onFailure(RealCall.this, ioException);
		      } finally {
		        if (!success) {
		          client.dispatcher().finished(this); // This call is no longer running!
		        }
		      }
		    }
		
		    @Override protected void execute() { //详情请见下方
		      boolean signalledCallback = false;
		      transmitter.timeoutEnter();
		      try {
		        Response response = getResponseWithInterceptorChain();
		        signalledCallback = true;
		        responseCallback.onResponse(RealCall.this, response);
		      } catch (IOException e) {
		        if (signalledCallback) {
		          // Do not signal the callback twice!
		          Platform.get().log(INFO, "Callback failure for " + toLoggableString(), e);
		        } else {
		          responseCallback.onFailure(RealCall.this, e);
		        }
		      } finally {
		        client.dispatcher().finished(this);
		      }
		    }
		 	 }

		---
			 $NamedRunnable.java 
			//抽象的接口 ， 原来也是sh自 Runnable
			public abstract class NamedRunnable implements Runnable {
			  protected final String name;
			
			  public NamedRunnable(String format, Object... args) {
			    this.name = Util.format(format, args);
			  }
			
			  @Override public final void run() {
			    String oldName = Thread.currentThread().getName();
			    Thread.currentThread().setName(name); //设置一下线程名
			    try {
			      execute(); //执行网络请求的时候，线程池执行这个run，
			    } finally {
			      Thread.currentThread().setName(oldName); //线程 名设置回来
			    }
			  }
			
			  protected abstract void execute(); // 干的漂亮！！好一个模板设计模式
			}
					
		---
			$Dispatcher.java
			  void enqueue(AsyncCall call) {
			    synchronized (this) { //加对象锁，保证安全
			      readyAsyncCalls.add(call); //将这个AsyncCall加入ReadyAsyncCalls集合当中
			
			      // Mutate the AsyncCall so that it shares the AtomicInteger of an existing running call to
			      // the same host.
			      if (!call.get().forWebSocket) { //这位兄台，你不是webSocket吧
			        AsyncCall existingCall = findExistingCallWithHost(call.host()); //是否已经存在这个url
			        if (existingCall != null) call.reuseCallsPerHostFrom(existingCall);
			      }
			    }
			    promoteAndExecute();
			  }
			
			  private boolean promoteAndExecute() {
			    assert (!Thread.holdsLock(this));
			
			    List<AsyncCall> executableCalls = new ArrayList<>();
			    boolean isRunning;
			    synchronized (this) {
			      for (Iterator<AsyncCall> i = readyAsyncCalls.iterator(); i.hasNext(); ) {
			        AsyncCall asyncCall = i.next();
					//请求数量超标，直接break  进入下面的环节
			        if (runningAsyncCalls.size() >= maxRequests) break; // Max capacity.
					//同一个请求达到最大请求数 ，跳过当前的这个url,continue到下一个
			        if (asyncCall.callsPerHost().get() >= maxRequestsPerHost) continue; // Host max capacity.
			
			        i.remove(); // 从 readyAsyncCalls 中移除
					// 一个请求对应一个AsyncCall,增加当前这个请求的计数
			        asyncCall.callsPerHost().incrementAndGet();
					//将readyAsyncCalls 加入到executableCalls和runningAsyncCalls中
			        executableCalls.add(asyncCall);
			        runningAsyncCalls.add(asyncCall);
			      }
			      isRunning = runningCallsCount() > 0;
			    }
				//ok,一切就绪
			    for (int i = 0, size = executableCalls.size(); i < size; i++) {
			      AsyncCall asyncCall = executableCalls.get(i);
				//!! 全国人民 注意啦，带着统一的ExecutorService回到AsyncTask的内部去执行起来吧。
			      asyncCall.executeOn(executorService());
			    }
			    return isRunning;
			  }
				//获取DisPatcher中的ExecutorService
				  public synchronized ExecutorService executorService() {
				    if (executorService == null) {
				      executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
				          new SynchronousQueue<>(), Util.threadFactory("OkHttp Dispatcher", false));
				    }
				    return executorService;
		  }

		---
		 * ok回到AsyncCall,是时候表演真正的技术了
		
				/**
			     * Attempt to enqueue this async call on {@code executorService}. This will attempt to clean up
			     * if the executor has been shut down by reporting the call as failed.
			     */
			    void executeOn(ExecutorService executorService) {
			      assert (!Thread.holdsLock(client.dispatcher())); //未持有当前线程的对象锁，给我继续！否则~~Exception伺候
			      boolean success = false;
			      try {
					//她来了~ 她来了~ 她带着任务来了
			        executorService.execute(this); //传入this,是因为这个AsyncCall继承了NamedRunnable也就是一个Runnable
			        success = true;
			      } catch (RejectedExecutionException e) { //请求太多了，线程池大哥已经废了，但是目前看不可能出现这个异常的
			        InterruptedIOException ioException = new InterruptedIOException("executor rejected");
			        ioException.initCause(e);
			        transmitter.noMoreExchanges(ioException);
			        responseCallback.onFailure(RealCall.this, ioException);
			      } finally {
			        if (!success) {
			          client.dispatcher().finished(this); // This call is no longer running!
			        }
			      }
			    }	
				//上方的execotorService一旦执行起来，这个写在run中的抽象execute()方法即在线程池中执行起来
			    @Override protected void execute() {
			      boolean signalledCallback = false;
			      transmitter.timeoutEnter();//看门狗，超时判断 这个是什么东西。
			      try {
			        Response response = getResponseWithInterceptorChain(); //来了 来了 她带着Response和Interceptor来了
			        signalledCallback = true;
			        responseCallback.onResponse(RealCall.this, response);
			      } catch (IOException e) {
			        if (signalledCallback) {
			          // Do not signal the callback twice!
			          Platform.get().log(INFO, "Callback failure for " + toLoggableString(), e);
			        } else {
			          responseCallback.onFailure(RealCall.this, e);
			        }
			      } finally {
			        client.dispatcher().finished(this);
			      }
			    }
			 	 }	

---

### 来了，来了，她脚踩五彩祥云，确认过眼神是个盖世英雄，带着拦截器来了~~~
---
	

			* RealCall.java

					//getResponseWithInterceptorChain这也是RealCall中的最后一个方法，我想应该也是有某些意思吧。
					  Response getResponseWithInterceptorChain() throws IOException {
				    // Build a full stack of interceptors.
				    List<Interceptor> interceptors = new ArrayList<>();
					//先把用户在OkHttpClient中添加进去的拦截器 弄进去
				    interceptors.addAll(client.interceptors()); 
					// RetryAndFollowUpInterceptor 拦截器  --> 1
				    interceptors.add(new RetryAndFollowUpInterceptor(client));
					// BridgeInterceptor 拦截器            --> 2
				    interceptors.add(new BridgeInterceptor(client.cookieJar()));
					// CacheInterceptor 拦截器			   --> 3
				    interceptors.add(new CacheInterceptor(client.internalCache()));
					// ConnectInterceptor 拦截器 		   --> 4
				    interceptors.add(new ConnectInterceptor(client));
				    if (!forWebSocket) { //是webSocket的话就别加这个 networkInterceptors 拦截器 --> 5
				      interceptors.addAll(client.networkInterceptors());
				    }
					// CallServerInterceptor 拦截器  -->  6
				    interceptors.add(new CallServerInterceptor(forWebSocket));
					// RealInterceptorChain 拦截器 
				    Interceptor.Chain chain = new RealInterceptorChain(interceptors, transmitter, null, 0,
				        originalRequest, this, client.connectTimeoutMillis(),
				        client.readTimeoutMillis(), client.writeTimeoutMillis());
				
				    boolean calledNoMoreExchanges = false;
				    try {
						// RealInterceptorChain 带着 originalRequest 来了 
				      Response response = chain.proceed(originalRequest); //ok , 我们到RealInterceptorChain中看一看proceed方法
				      if (transmitter.isCanceled()) {
				        closeQuietly(response);
				        throw new IOException("Canceled");
				      }
				      return response;
				    } catch (IOException e) {
				      calledNoMoreExchanges = true;
				      throw transmitter.noMoreExchanges(e);
				    } finally {
				      if (!calledNoMoreExchanges) {
				        transmitter.noMoreExchanges(null);
				      }
				    }
				 	 }

	 $ RealInterceptorChain.java

		@Override public Response proceed(Request request) throws IOException {
	    return proceed(request, transmitter, exchange);
	  }
	
	  public Response proceed(Request request, Transmitter transmitter, @Nullable Exchange exchange)
	      throws IOException {
	    if (index >= interceptors.size()) throw new AssertionError();
	
	    calls++;
	
	    // If we already have a stream, confirm that the incoming request will use it.
	    if (this.exchange != null && !this.exchange.connection().supportsUrl(request.url())) {
	      throw new IllegalStateException("network interceptor " + interceptors.get(index - 1)
	          + " must retain the same host and port");
	    }
	
	    // If we already have a stream, confirm that this is the only call to chain.proceed().
	    if (this.exchange != null && calls > 1) {
	      throw new IllegalStateException("network interceptor " + interceptors.get(index - 1)
	          + " must call proceed() exactly once");
	    }
		// 调用下一个拦截器
	    // Call the next interceptor in the chain.
		//封装了这个RealInterceptorChain index从0 --> 1 
	    RealInterceptorChain next = new RealInterceptorChain(interceptors, transmitter, exchange,
	        index + 1, request, call, connectTimeout, readTimeout, writeTimeout);
		
	    Interceptor interceptor = interceptors.get(index); //集合中取第1个，用户的拦截器，假定用户没有传入拦截器，那么第一个为RetryAndFollowUpInterceptor这个拦截器
	    Response response = interceptor.intercept(next); // 包裹着这个RealInterceptorChain 我进入RetryAndFollowUpInterceptor取看这个inercept(Chin)
	
	    // Confirm that the next interceptor made its required call to chain.proceed().
	    if (exchange != null && index + 1 < interceptors.size() && next.calls != 1) {
	      throw new IllegalStateException("network interceptor " + interceptor
	          + " must call proceed() exactly once");
	    }
	
	    // Confirm that the intercepted response isn't null.
	    if (response == null) {
	      throw new NullPointerException("interceptor " + interceptor + " returned null");
	    }
	
	    if (response.body() == null) {
	      throw new IllegalStateException(
	          "interceptor " + interceptor + " returned a response with no body");
	    }
	
	    return response;
	  }

![](https://raw.githubusercontent.com/AKJoson/AKJoson.github.io/master/okHttpInterceptor.jpg)
![](https://raw.githubusercontent.com/AKJoson/AKJoson.github.io/master/okhttp.jpg)

		
			
			
