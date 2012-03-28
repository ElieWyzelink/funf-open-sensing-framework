package edu.mit.media.funf.probe;

import static edu.mit.media.funf.Utils.TAG;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import edu.mit.media.funf.DataNormalizer;
import edu.mit.media.funf.HashUtil;
import edu.mit.media.funf.HashUtil.HashingType;
import edu.mit.media.funf.JsonUtils;
import edu.mit.media.funf.Utils;
import edu.mit.media.funf.json.BundleTypeAdapter;
import edu.mit.media.funf.probe.builtin.ProbeKeys.BaseProbeKeys;

public interface Probe {
	
	public static final boolean DEFAULT_OPPORTUNISTIC = true;
	public static final boolean DEFAULT_STRICT = false;
	
	
	/**
	 * Set a Context to give this probe access to system resources.
	 * @param context
	 */
	public void setContext(Context context);
	
	
	/**
	 * Changes the configuration for this probe.  Setting the configuration will disable the probe.
	 * @param config
	 */
	public void setConfig(JsonObject config);
	
	/**
	 * @return A copy of the current specified configuration for this probe.
	 */
	public JsonObject getConfig();
	
	/**
	 * @return A copy of all configuration parameters, including default ones.
	 */
	public JsonObject getCompleteConfig();
	
	/**
	 * @return
	 */
	public Uri getProbeUri();
	
	/**
	 * @return
	 */
	public Uri getCompleteProbeUri();
	
	/**
	 * Sets the ProbeFactory this probe should use for accessing other probes.
	 * @param factory
	 */
	public void setProbeFactory(ProbeFactory factory);

	/**
	 * Listeners added to this probe will receive data callbacks from this probe,
	 * until this listener is unregistered.  The probe should continue to be active
	 * and send data to this listener until it is unregistered.
	 * This method is indempotent and thread safe.
	 * @param listener
	 */
	public void registerListener(DataListener... listener);
	
	/**
	 * Remove all listeners and disable;
	 */
	public void destroy();

	/**
	 * @return the current state of the probe.
	 */
	public State getState();
	public void addStateListener(StateListener listener);
	public void removeStateListener(StateListener listener);
	

	public static final double DEFAULT_PERIOD = 3600; 
	
	public interface PassiveProbe extends Probe {


		/**
		 * Listeners removed from this probe will no longer receive data callbacks from this probe.
		 * This method is indempotent and thread safe.
		 * @param listener
		 */
		public void registerPassiveListener(DataListener... listener);
		
		/**
		 * Listeners removed from this probe will no longer receive data callbacks from this probe.
		 * This method is indempotent and thread safe.
		 * 
		 * @param listener
		 */
		public void unregisterPassiveListener(DataListener... listener);
	}
	
	public interface ContinuousProbe extends Probe {
		public static final double DEFAULT_DURATION = 60; // One minute
		
		/**
		 * Listeners removed from this probe will no longer receive data callbacks from this probe.
		 * This method is indempotent and thread safe.
		 * @param listener
		 */
		public void unregisterListener(DataListener... listener);
		
	}
	
	public interface DiffableProbe extends Probe {
		// TODO: think of better names for these functions
		public boolean isDiffConfigured();
		public void setDiffParams(JsonObject diffParamValue);
		public JsonObject getUpdatedDiffParams(JsonObject oldDiffParamValue, JsonObject... newData);
	}
	
	@Documented
	@Retention(RUNTIME)
	@Target(TYPE)
	@Inherited
	public @interface DefaultSchedule {
		String value() default "";
		double period() default Probe.DEFAULT_PERIOD;
		double duration() default ContinuousProbe.DEFAULT_DURATION;
		boolean opportunistic() default DEFAULT_OPPORTUNISTIC;
		boolean strict() default DEFAULT_STRICT;
	}
	
	/**
	 * Used to indicate that a particular field is configurable for the probe.
	 * The name of the field will be used as the configuration name, and the 
	 * default value will be calculated by creating an instance with no config
	 * and inspecting what value gets created.
	 *
	 */
	@Documented
	@Retention(RUNTIME)
	@Target(FIELD)
	@Inherited
	public @interface Configurable {
		/**
		 * @return Overrides the field name, if specified.
		 */
		String name() default "";
	}

	@Documented
	@Retention(RUNTIME)
	@Target(TYPE)
	@Inherited
	public @interface DisplayName {
		String value();
	}
	
	@Documented
	@Retention(RUNTIME)
	@Target(TYPE)
	@Inherited
	public @interface Description {
		String value();
	}
	
	@Documented
	@Retention(RUNTIME)
	@Target(TYPE)
	@Inherited
	public @interface RequiredPermissions {
		String[] value();
	}
	
	@Documented
	@Retention(RUNTIME)
	@Target(TYPE)
	@Inherited
	public @interface RequiredFeatures {
		String[] value();
	}
	
	/**
	 * Interface implemented by Probe data observers.
	 */
	public interface DataListener {

		/**
		 * Called when the probe emits data.  Data emitted from probes that extend the Probe class
		 * are guaranteed to have the PROBE and TIMESTAMP parameters.
		 * @param data
		 */
		public void onDataReceived(Uri completeProbeUri, JsonObject data);
		
		public void onDataCompleted(Uri completeProbeUri);
	}
	

	/**
	 * Interface implemented by Probe status observers.
	 */
	public interface StateListener {

		/**
		 * Called when the probe emits a status message, which can happen when the probe changes state.
		 * @param status
		 */
		public void onStateChanged(Probe probe);
	}
	
	public class Identifier {
		public static final String PROBE_SCHEME = "probe";
		
		/**
		 * A consistent Uri for the probe class and config pair.  This can be used as an identifier for a probe with a specific config.
		 * Need to ensure that your config string is consistently sorted.
		 * @return
		 */
		public static Uri getProbeUri(String probeName, String config) {
			String path = config == null ? "" : "/" + config;
			return new Uri.Builder()
					.scheme(PROBE_SCHEME)
					.authority(probeName)
					.path(path)
					.build();
		}
		
		/**
		 * A consistent Uri for the probe class and config pair.  This can be used as an identifier for a probe with a specific config.
		 * Will sort the JsonObject before serializing to the Uri, to ensure a consistent Uri.
		 * @param probeName
		 * @param config
		 * @return
		 */
		public static Uri getProbeUri(String probeName, JsonObject config) {
			return getProbeUri(probeName, 
					(config == null) ? null : JsonUtils.deepSort(config).toString());
		}

		/**
		 * A consistent Uri for the probe class and config pair.  This can be used as an identifier for a probe with a specific config.
		 * Will sort the JsonObject before serializing to the Uri, to ensure a consistent Uri.
		 * @param probeClass
		 * @param config
		 * @return
		 */
		public static Uri getProbeUri(Class<? extends Probe> probeClass, JsonObject config) {
			return getProbeUri(probeClass.getName(), config);
		}
		
		/**
		 * A consistent Uri for the probe class and config pair.  This can be used as an identifier for a probe with a specific config.
		 * @param probeClass
		 * @param config
		 * @return
		 */
		public static Uri getProbeUri(Class<? extends Probe> probeClass, String config) {
			return getProbeUri(probeClass.getName(), config);
		}
		
		/**
		 * A consistent Uri for the probe class and config pair.  This can be used as an identifier for the probe instance.
		 * Will sort the JsonObject before serializing to the Uri, to ensure a consistent Uri.
		 * @param probe
		 * @return
		 */
		public static Uri getProbeUri(Probe probe) {
			return getProbeUri(probe.getClass(), probe.getConfig());
		}
		
		public static Uri getCompleteProbeUri(Probe probe) {
			return getProbeUri(probe.getClass(), probe.getCompleteConfig());
		}
		
		/**
		 * Returns true if Uri is a probe Uri.
		 * @param probeUri
		 * @return
		 */
		public static boolean isProbeUri(Uri probeUri) {
			return PROBE_SCHEME.equalsIgnoreCase(probeUri.getScheme());
		}
		
		private static void verifyProbeUri(Uri probeUri) {
			if (!isProbeUri(probeUri)) {
				throw new RuntimeException("Uri is not a probe Uri: " + probeUri.toString());
			}
		}
		
		/**
		 * Returns the name of the probe in the Uri.
		 * @param probeUri
		 * @return
		 */
		public static String getProbeName(Uri probeUri) {
			verifyProbeUri(probeUri);
			return probeUri.getAuthority();
		}
		
		/**
		 * Returns the config in the probe Uri as a String.
		 * @param probeUri
		 * @return
		 */
		public static String getConfigString(Uri probeUri) {
			verifyProbeUri(probeUri);
			String path = probeUri.getPath();
			return path == null || !path.startsWith("/") ? null : path.substring(1);
		}
		
		/**
		 * Returns the config in the probe Uri as a JsonObject.
		 * @param probeUri
		 * @return
		 */
		public static JsonObject getConfig(Uri probeUri) {
			String configString = getConfigString(probeUri);
			if (configString == null || configString.length() == 0) {
				return null;
			} else {
				return (JsonObject)new JsonParser().parse(configString);
			}
		}
	}
	
	/**
	 * Types to represent the current state of the probe.
	 * Provides the implementation of the ProbeRunnable state machine.
	 *
	 */
	public static enum State {

		// TODO: should we try catch, to prevent one probe from killing all probes?
		DISABLED {

			@Override
			protected void enable(Base probe) {
				synchronized (probe) {
					probe.state = ENABLED;
					probe.onEnable();
					probe.notifyStateChange();
				}
			}
	
			@Override
			protected void start(Base probe) {
				synchronized (probe) {
					enable(probe);
					if (probe.state == ENABLED) {
						ENABLED.start(probe);
					}
				}
			}
	
			@Override
			protected void stop(Base probe) {
				// Nothing
			}
	
			@Override
			protected void disable(Base probe) {
				// Nothing
			}
		},
		ENABLED {

			@Override
			protected void enable(Base probe) {
				// Nothing
			}
	
			@Override
			protected void start(Base probe) {
				synchronized (probe) {
					if (probe.isWakeLockedWhileRunning()) {
						Uri probeUri = Probe.Identifier.getProbeUri(probe);
						probe.lock = Utils.getWakeLock(probe.getContext(), probeUri.toString());
					}
					probe.state = RUNNING;
					probe.onStart();
					probe.notifyStateChange();
				}
			}
	
			@Override
			protected void stop(Base probe) {
				// Nothing
			}
	
			@Override
			protected void disable(Base probe) {
				synchronized (probe) {
					probe.state = DISABLED;
					probe.onDisable();
					probe.notifyStateChange();
					probe.passiveDataListeners.clear();
					probe.dataListeners.clear();
					// Shutdown handler thread
					probe.looper.quit();
					probe.looper = null;
					probe.handler = null;
				}
			}
		},
		RUNNING {
 
			@Override
			protected void enable(Base probe) {
				// Nothing
			}
	
			@Override
			protected void start(Base probe) {
				// Nothing
			}
	
			@Override
			protected void stop(Base probe) {
				synchronized (probe) {
					probe.state = ENABLED;
					probe.onStop();
					probe.notifyStateChange();
					if (probe.lock != null && probe.lock.isHeld()) {
						probe.lock.release();
						probe.lock = null;
					}
				}
			}
	
			@Override
			protected void disable(Base probe) {
				synchronized (probe) {
					stop(probe);
					if (probe.state == ENABLED) {
						ENABLED.disable(probe);
					}
				}
			}
		};
		
		protected abstract void enable(Base probe);
		protected abstract void disable(Base probe);
		protected abstract void start(Base probe);
		protected abstract void stop(Base probe);
		
	}

	@DefaultSchedule
	public abstract class Base implements Probe, BaseProbeKeys {
		
		/**
		 * No argument constructor requires that setContext be called manually.
		 */
		public Base() {
			state = State.DISABLED;
		}
		
		public Base(Context context) {
			this();
			setContext(context);
		}
		
		public Base(Context context, ProbeFactory factory) {
			this(context);
			setProbeFactory(factory);
		}
		
		private Gson gson;
		protected Gson getGson() {
			if (gson == null) {
				gson = getGsonBuilder().create(); 
			}
			return gson;
		}
		
		protected GsonBuilder getGsonBuilder() {
			TypeAdapterFactory adapterFactory = getSerializationFactory();
			GsonBuilder builder = new GsonBuilder();
			if (adapterFactory != null) {
				builder.registerTypeAdapterFactory(adapterFactory);
			}
			builder.registerTypeAdapterFactory(BundleTypeAdapter.FACTORY);
			return builder;
		}
		
		// TODO: figure out how to get scheduler to use source data requests to schedule appropriately
		// Probably will need to prototype with ActivityProbe
		public Map<String,JsonObject> getSourceDataRequests() {
			return null;
		}
		
		public JsonObject getDiffConfig(Bundle existingConfig, JsonObject... data) {
			return null;
		}
		
		private ProbeFactory probeFactory;
		public void setProbeFactory(ProbeFactory factory) {
			this.probeFactory = factory;
		}
		protected ProbeFactory getProbeFactory() {
			if (probeFactory == null) {
				synchronized (this) {
					if (probeFactory == null) {
						probeFactory = ProbeFactory.BasicProbeFactory.getInstance(getContext());
					}
				}
			}
			return probeFactory;
		}
	
		private Context context;
		public void setContext(Context context) {
			if (context == null) {
				throw new RuntimeException("Attempted to set a null context in probe '" + getClass().getName() + "'");
			}
			this.context = context.getApplicationContext();
		}
		protected Context getContext() {
			if (context == null) {
				throw new RuntimeException("Context was never set for probe '" + getClass().getName() + "'");
			}
			return context;
		}
		
		/*****************************************
		 * Probe Configuration
		 *****************************************/
		private JsonObject specifiedConfig;
		private JsonObject completeConfig;
		private Uri probeUri;
		private Uri completeProbeUri;

		private List<Field> configurableFields;
		private List<Field> getConfigurableFields() {
			if (configurableFields == null) {
				List<Field> newConfigurableFields = new ArrayList<Field>();
				Utils.getAllFieldsWithAnnotation(newConfigurableFields, getClass(), Configurable.class);
				configurableFields = newConfigurableFields;
			}
			return configurableFields;
		}

		@Override
		public synchronized void setConfig(JsonObject config) {
			disable();
			this.specifiedConfig = (config == null) ? null : new JsonObject();
			this.completeConfig = null;
			this.probeUri = null;
			this.completeProbeUri = null;
			if (this.specifiedConfig != null) {
				for (Field field : getConfigurableFields()) {
					// Only allow config that was declared
					Configurable configParamAnnotation = field.getAnnotation(Configurable.class);
					Class<?> type = field.getType();
					String name = configParamAnnotation.name();
					if (name.length() == 0) {
						name = field.getName();
					}
					boolean currentAccessibility = field.isAccessible();
					field.setAccessible(true);
					try {
						if (config.has(name)) {
							JsonElement el = config.get(name);
							specifiedConfig.add(name, el);
							Object value = getGson().fromJson(el, type);
							field.set(this, value);
						}
					} catch (IllegalArgumentException e) {
						Log.e(TAG, "Bad access of probe fields!!", e);
					} catch (IllegalAccessException e) {
						Log.e(TAG, "Bad access of probe fields!!", e);
					}
					field.setAccessible(currentAccessibility);
				}
			}
		}
		
		public JsonObject getConfig() {
			if (specifiedConfig == null) {
				synchronized (this) {
					if (specifiedConfig == null) {
						this.specifiedConfig = new JsonObject();
					}
				}
			}
			return JsonUtils.deepCopy(specifiedConfig);
		}
		
		
		/**
		 * Returns a copy of the default configuration that is used by the probe if no 
		 * configuration is specified.  This is also used to enumerate the 
		 * configuration options that are available.
		 * 
		 * The object returned by this function is a copy and can be modified.
		 * @return
		 */
		public static JsonObject getDefaultConfig(Class<? extends Probe> probeClass, Context context) {
			Probe defaultProbe = ProbeFactory.BasicProbeFactory.getInstance(context).getProbe(probeClass, null);
			return defaultProbe.getCompleteConfig();
		}
		

		
		/**
		 * Returns a copy of the current configuration for this probe in json format.
		 * @return
		 */
		public JsonObject getCompleteConfig() {
			if (completeConfig == null) {
				synchronized (this) {
					if (completeConfig == null) {
						completeConfig = new JsonObject();
						for (Field field : getConfigurableFields()) {
							Configurable configParamAnnotation = field.getAnnotation(Configurable.class);
							Class<?> type = field.getType();
							String name = configParamAnnotation.name();
							if (name.length() == 0) {
								name = field.getName();
							}
							boolean currentAccessibility = field.isAccessible();
							field.setAccessible(true);
							try {
								JsonElement value = getGson().toJsonTree(field.get(this), type);
								completeConfig.add(name, value);
							} catch (IllegalArgumentException e) {
								Log.e(TAG, "Bad access of probe fields!!", e);
							} catch (IllegalAccessException e) {
								Log.e(TAG, "Bad access of probe fields!!", e);
							}
							field.setAccessible(currentAccessibility);
						}
					}
				}
			}
			return JsonUtils.deepCopy(completeConfig);
		}
		
		public Uri getProbeUri() {
			if (probeUri == null) {
				probeUri = Identifier.getProbeUri(this);
			}
			return probeUri;
		}
		
		public Uri getCompleteProbeUri() {
			if (completeProbeUri == null) {
				completeProbeUri = Identifier.getCompleteProbeUri(this);
			}
			return completeProbeUri;
		}
		
	
		/*****************************************
		 * Probe Data Listeners
		 *****************************************/
		
	
		
		private Set<DataListener> dataListeners = Collections.synchronizedSet(new HashSet<DataListener>());
		private Set<DataListener> passiveDataListeners = Collections.synchronizedSet(new HashSet<DataListener>());
		
		/**
		 * Returns the set of data listeners.  Make sure to synchronize on this object, 
		 * if you plan to modify it or iterate over it.
		 */
		protected Set<DataListener> getDataListeners() {
			return dataListeners;
		}
		
		/**
		 * Returns the set of passive data listeners.  Make sure to synchronize on this object, 
		 * if you plan to modify it or iterate over it.
		 */
		protected Set<DataListener> getPassiveDataListeners() {
			return dataListeners;
		}
		
		@Override
		public void registerListener(DataListener... listeners) {
			if (listeners != null) {
				for (DataListener listener : listeners) {
					dataListeners.add(listener);
				}
				start();
			}
		}
	
		public void unregisterListener(DataListener... listeners) {
			if (listeners != null) {
				for (DataListener listener : listeners) {
					dataListeners.remove(listener);
				}
				// If no one is listening, stop using device resources
				if (dataListeners.isEmpty()) {
					stop();
				}
				if (passiveDataListeners.isEmpty()) {
					disable();
				}
			}
		}
		
		public void registerPassiveListener(DataListener... listeners) {
			if (listeners != null) {
				for (DataListener listener : listeners) {
					dataListeners.add(listener);
				}
				enable();
			}
		}
	
		public void unregisterPassiveListener(DataListener... listeners) {
			if (listeners != null) {
				for (DataListener listener : listeners) {
					dataListeners.remove(listener);
				}
				// If no one is listening, stop using device resources
				if (dataListeners.isEmpty() && passiveDataListeners.isEmpty()) {
					disable();
				}
			}
		}
		
		protected void notifyStateChange() {
			synchronized (stateListeners) {
				for (StateListener listener : stateListeners) {
					listener.onStateChanged(this);
				}
			}
		}
		
		protected void sendData(final JsonObject data) {
			if (data == null || looper == null) {
				return;
			} else if (Thread.currentThread() != looper.getThread()) {
				// Ensure the data send runs on the probe's thread
				Message dataMessage = handler.obtainMessage(SEND_DATA_MESSAGE, data);
				handler.sendMessage(dataMessage);
			} else {
				if (!data.has(TIMESTAMP)) {
					data.addProperty(TIMESTAMP, Utils.getTimestamp());
				}
				synchronized (dataListeners) {
					for (DataListener listener : dataListeners) {
						listener.onDataReceived(getCompleteProbeUri(), JsonUtils.deepCopy(data));
					}
				}
				synchronized (passiveDataListeners) {
					for (DataListener listener : passiveDataListeners) {
						if (!dataListeners.contains(listener)) { // Don't send data twice to passive listeners
							listener.onDataReceived(getCompleteProbeUri(), JsonUtils.deepCopy(data));
						}
					}
				}
			}
		}
		
		
		
		/*****************************************
		 * Probe State Machine
		 *****************************************/
		
		private State state;
		private PowerManager.WakeLock lock;
		
		@Override
		public State getState() {
			return state;
		}
		
		private synchronized void ensureLooperThreadExists() {
			if (looper == null) {
				HandlerThread thread = new HandlerThread("Probe[" + getClass().getName() + "]");
		        thread.start();
		        looper = thread.getLooper();
		        handler = new Handler(looper, new ProbeHandlerCallback());
			}
		}
		
		protected final synchronized void enable() {
			ensureLooperThreadExists();
			handler.post(new Runnable() {
				@Override
				public void run() {
					state.enable(Base.this);
				}
			});
		}
	
		protected final synchronized void start() {
			ensureLooperThreadExists();
			handler.post(new Runnable() {
				@Override
				public void run() {
					state.start(Base.this);
				}
			});
		}
	
		protected final synchronized void stop() {
			ensureLooperThreadExists();
			handler.post(new Runnable() {
				@Override
				public void run() {
					state.stop(Base.this);
				}
			});
		}
	
		protected final synchronized void disable() {
			if (handler != null) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						state.disable(Base.this);
					}
				});
			}
		}
		

		public void destroy() {
			disable();
		}
		
	
		/**
		 * Called when the probe switches from the disabled to the enabled state.  
		 * This is where any passive or opportunistic listeners should be configured.
		 * An enabled probe should not keep a wake lock.  If you need 
		 * the device to stay awake consider implementing a StartableProbe, and
		 * using the onStart method.
		 */
		protected void onEnable() {
			
		}
		
		/**
		 * Called when the probe switches from the enabled state to active running state.  
		 * This should be used to send any data broadcasts, but must return quickly.
		 * If you have any long running processes they should be started on a separate thread 
		 * created by this method, or should be divided into short runnables that are posted to 
		 * this threads looper one at a time, to allow for the probe to change state.
		 */
		protected void onStart() {
			
		}
		
		/**
		 * Called with the probe switches from the running state to the enabled state.  
		 * This method should be used to stop any running threads emitting data, or remove
		 * a runnable that has been posted to this thread's looper.
		 * Any passive listeners should continue running.
		 */
		protected void onStop() {
			
		}
		
		/**
		 * Called with the probe switches from the enabled state to the disabled state.  
		 * This method should be used to stop any passive listeners created in the onEnable method.
		 * This is the time to cleanup and release any resources before the probe is destroyed.
		 */
		protected void onDisable() {
			
		}
		
		
	
		private volatile Looper looper;
		private volatile Handler handler;
		
		/**
		 * Access to the probe thread's handler.
		 * @return
		 */
		protected Handler getHandler() {
			return handler;
		}
		
		/**
		 * @param msg
		 * @return
		 */
		protected boolean handleMessage(Message msg) {
			// For right now don't handle any messages, only runnables
			return false;
		}
		
		protected static final int SEND_DATA_MESSAGE = 534334;
		
		private class ProbeHandlerCallback implements Handler.Callback {
	
			@Override
			public boolean handleMessage(Message msg) {
				if (msg.what == SEND_DATA_MESSAGE && msg.obj instanceof JsonObject) {
					sendData((JsonObject)msg.obj);
					return true;
				} else {
					return Base.this.handleMessage(msg);
				}
			}
			
		}
		
		
	
		
		/*****************************************
		 * Probe State Listeners
		 *****************************************/
		
		
		private Set<StateListener> stateListeners = Collections.synchronizedSet(new HashSet<StateListener>());
		
		/**
		 * Returns the set of status listeners.  Make sure to synchronize on this object, 
		 * if you plan to modify it or iterate over it.
		 */
		protected Set<StateListener> getStateListeners() {
			return stateListeners;
		}
		
		@Override
		public void addStateListener(StateListener listener) {
			stateListeners.add(listener);
		}
	
		@Override
		public void removeStateListener(StateListener listener) {
			stateListeners.remove(listener);
		}
		
		

		public static Class<? extends Probe> getProbeClass(String probeDescriptor) {
			// TODO: Implement uri signatures, and find best available class
			try {
			 	Class<?> theClass = Class.forName(probeDescriptor);
				if (Probe.class.isAssignableFrom(theClass)) {
					@SuppressWarnings("unchecked")
					Class<? extends Probe> probeClass = (Class<? extends Probe>)theClass;
					return probeClass;
				}
			} 
			catch (ClassNotFoundException e) {
				Log.e(TAG, "Probe does not exist: '" + probeDescriptor + "'", e);
			}
			return null;
		}
		
		
		/**********************************
		 * Sensitive Data
		 ********************************/
		
		@Configurable
		private boolean hideSensitiveData = true;
		
		protected String sensitiveData(String data) {
			return sensitiveData(data, null);
		}
		
		protected String sensitiveData(String data, DataNormalizer<String> normalizer) {
			if (hideSensitiveData) {
				if (normalizer != null) {
					data = normalizer.normalize(data);
				}
				return HashUtil.hashString(getContext(), data, HashingType.ONE_WAY_HASH);
			} else {
				return data;
			}
		}
		
		/**********************************
		 * Custom serialization
		 ********************************/
		
		/**
		 * Used to override the serialiazation technique for multiple types
		 * @return
		 */
		protected TypeAdapterFactory getSerializationFactory() {
			return null;
		}
		
		protected boolean isWakeLockedWhileRunning() {
			return true;
		}
	}
}
