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
		
			$ MultipartBody.java  //compliant request body 即 兼容请求体
			
		
			
			
