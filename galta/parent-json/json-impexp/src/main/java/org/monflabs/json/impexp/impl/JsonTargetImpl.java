/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.monflabs.json.impexp.impl;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

import org.monflabs.json.JsonException;
import org.monflabs.json.JsonUtil;
import org.monflabs.json.impexp.CancelException;
import org.monflabs.json.impexp.ImportResult;
import org.monflabs.json.impexp.JsonContent;
import org.monflabs.json.impexp.JsonContent.TYPE;
import org.monflabs.json.impexp.JsonKey;
import org.monflabs.json.impexp.JsonSource;
import org.monflabs.json.impexp.JsonTarget;
import org.monflabs.json.impexp.replication.ConflictResolver;
import org.monflabs.json.impexp.replication.RangeFilter;
import org.monflabs.json.impexp.replication.ReplicationResult;
import org.monflabs.json.impexp.replication.ReplicationSource;
import org.monflabs.json.impexp.replication.ReplicationTable;
import org.monflabs.json.impexp.replication.ReplicationTarget;
import org.monflabs.util.Console;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.StringFormat;

/**
 * Predefined JsonContent collector.
 */
public abstract class JsonTargetImpl implements JsonTarget {

	public static final int DEFAULT_NOTIFICATION_DELAY = 2000;

	public static abstract class TargetBuilder<C extends JsonTarget, T extends TargetBuilder<C,?>> extends ObjectBuilder<C> {
		private ReplicationTable replicationTable;
		private LongSupplier estimatedCountSupplier;
		private int notificationDelay = DEFAULT_NOTIFICATION_DELAY;
		private Notification notification;
		private int transactionThreshold = Integer.MAX_VALUE;
		private Function<JsonContent,JsonContent> beforeProcessing;
		private Consumer<JsonContent> afterProcessing;

		@SuppressWarnings("unchecked")
		public T replicationTable(ReplicationTable replicationTable) {
			this.replicationTable = replicationTable;
			return (T)this;
		}
		@SuppressWarnings("unchecked")
		public T estimatedCount(LongSupplier estimatedCountSupplier) {
			this.estimatedCountSupplier = estimatedCountSupplier;
			return (T)this;
		}
		@SuppressWarnings("unchecked")
		public T notificationDelay(int notificationDelay) {
			this.notificationDelay = notificationDelay;
			return (T)this;
		}
		@SuppressWarnings("unchecked")
		public T notification(JsonTargetImpl.Notification notification) {
			this.notification = notification;
			return (T)this;
		}
		@SuppressWarnings("unchecked")
		public T transactionThreshold(int transactionThreshold) {
			this.transactionThreshold = transactionThreshold;
			return (T)this;
		}
		@SuppressWarnings("unchecked")
		public T beforeProcessing(Function<JsonContent,JsonContent> beforeProcessing) {
			this.beforeProcessing = beforeProcessing;
			return (T)this;
		}
		@SuppressWarnings("unchecked")
		public T afterProcessing(Consumer<JsonContent> afterProcessing) {
			this.afterProcessing = afterProcessing;
			return (T)this;
		}
	}

	public static enum Event {
		START, PROCESS, CANCEL, END
	}

	public interface Notification { 
		public void notify(Event event, long processed, long deleted, long estimatedCount, long ellapsedMs);
		public void information(String msg, Object...params);
	}

	private ReplicationTable replicationTable;
	private LongSupplier estimatedCountSupplier;
	private int notificationDelay;
	private Notification notification;
	private int transactionThreshold;
	private Function<JsonContent,JsonContent> beforeProcessing;
	private Consumer<JsonContent> afterProcessing;
	
	// The engine currently running, if any, so it can be cancelled
	private volatile Engine runningEngine;

	public static abstract class TextNotification implements Notification {
		@Override
		public void notify(Event event, long processed, long deleted, long estimatedCount, long ellapsedMs) {
			if(event==Event.START) {
				if(estimatedCount>0) {
					log("Start processing ~{0} documents",estimatedCount);
				} else {
					log("Start processing");
				}
				return;
			}
			if(event==Event.CANCEL) {
				log("Process cancelled");
				return;
			}
			if(event==Event.END) {
				log("Finished processing {0} records ({1} deleted) in {2} seconds...",processed,deleted,ellapsedMs/1000);
				return;
			}
			if(estimatedCount>0) {
				int percent = (int)((100L*processed)/estimatedCount);
				if(processed!=0) {
					long left = (estimatedCount-processed)*ellapsedMs/processed/1000L;
					log("{0}/{1} - {2}% records processed ({3} deleted) in {4} seconds ({5}s left)", 
								processed, estimatedCount, percent, deleted, ellapsedMs/1000L, left);
				} else {
					log("{0}/{1} - {2}% records processed ({3} deleted) in {4} seconds", 
							processed, estimatedCount, percent, deleted, ellapsedMs/1000L);
				}
			} else {
				log("{0} records processed ({1} deleted) in {2} seconds", processed,deleted,  ellapsedMs/1000);
			}
		}
		@Override
		public void information(String msg, Object...params) {
			log(msg, params);
		}
		public abstract void log(String msg, Object...params);
	};
	public static Notification consoleLogger = new TextNotification() {
		@Override
		public void log(String msg, Object...params) {
			Console.log(msg,params);
		}
	};

	protected JsonTargetImpl(TargetBuilder<?,?> builder) {
		this.replicationTable = builder.replicationTable;
		this.estimatedCountSupplier = builder.estimatedCountSupplier;
		this.notificationDelay = builder.notificationDelay;
		this.notification = builder.notification;
		this.transactionThreshold = builder.transactionThreshold;
		this.beforeProcessing = builder.beforeProcessing;
		this.afterProcessing = builder.afterProcessing;
	}
	
	@Override
	public boolean supportsDeletions() {
		return false;
	}
	
	public ReplicationTable getReplicationTable() {
		return replicationTable;
	}
	
	public int getTransactionThreshold() {
		return transactionThreshold;
	}
	
    public Notification getNotification() {
		return notification;
	}

    
	@Override
	public ImportResult importFrom(JsonSource source, RangeFilter filter) {
		ImportEngine engine = getImportEngine(source,filter);
		return engine.importData();
	}
	
	/**
	 * Requests the running import or replication to stop.
	 * <p>
	 * This can be called from any thread, including from a notification or a
	 * processing callback. The engine stops before saving the next content: the pending
	 * transaction, if any, is rolled back, the target is closed and the import (or
	 * replication) throws a {@link CancelException}.
	 * 
	 * @return true if an import or a replication was running
	 */
	public boolean cancel() {
		Engine e = runningEngine;
		if(e!=null) {
			e.cancel = true;
			return true;
		}
		return false;
	}
	
	public synchronized ReplicationResult replicate(ReplicationSource source, ConflictResolver resolver) {
		if(replicationTable==null) {
			throw new JsonException(null,"Replication table should not be null");
		}
		if(!(this instanceof ReplicationTarget)) {
			throw new JsonException(null,"{0} cannot be the target of a replication: it does not implement {1}", getClass().getName(), ReplicationTarget.class.getName());
		}
		ReplicationEngine engine = getReplicationEngine(source, resolver);
		return engine.replicate();
	}
	
	protected ImportEngine getImportEngine(JsonSource source, RangeFilter filter) {
		return new ImportEngine(source,filter);
	}
	
	protected ReplicationEngine getReplicationEngine(ReplicationSource source, ConflictResolver resolver) {
		return new ReplicationEngine(source,resolver);
	}


    
    public void init() {
	}
	@Override
	public void close() {
	}

    
	protected JsonContent readJsonContent(JsonKey key) {
		throw new IllegalStateException(StringFormat.format("Cannot read content for {0}", getClass().getName()));
	}
	protected abstract void saveJsonContent(JsonContent content);
	
	

	protected boolean supportsTransaction() {
		return false;
	}
	protected void startTransaction() {
		if(!supportsTransaction()) {
			throw new IllegalStateException(StringFormat.format("Transactions are not enabled for {0}", getClass().getName()));
		}
	}
	protected void commitTransaction() {
		if(!supportsTransaction()) {
			throw new IllegalStateException(StringFormat.format("Transactions are not enabled for {0}", getClass().getName()));
		}
	}
	protected void rollbackTransaction() {
		if(!supportsTransaction()) {
			throw new IllegalStateException(StringFormat.format("Transactions are not enabled for {0}", getClass().getName()));
		}
	}

	

	
	//
	// Import engine
	//

	protected class Engine {
		
		protected int transactionCount;
		protected volatile boolean cancel;
		protected long estimatedCount;

		protected long startTS;
		protected long lastTS;
		protected boolean closed;
		// True while a transaction started by this engine is pending (neither committed nor rolled back)
		protected boolean transactionOpen;
		
		protected void initEngine() {
			closed = false;
			cancel = false;
			runningEngine = this;
			init();
			estimatedCount = estimatedCountSupplier!=null ? estimatedCountSupplier.getAsLong() : -1;
			startTS = lastTS = System.currentTimeMillis();
			transactionCount = 0;
			transactionOpen = false;
			notify(JsonTargetImpl.Event.START, 0, 0, estimatedCount, 0);				
		}
		protected void closeEngine(ImportResult result) {
			closed = true;
			runningEngine = null;
			close();
			lastTS = System.currentTimeMillis();
			result.setDuration(lastTS-startTS);
			notify(JsonTargetImpl.Event.END, result.getProcessed(), result.getDeleted(), estimatedCount, result.getDuration());	
		}
		/**
		 * Rollback the pending transaction (when there is one) and close the target,
		 * without letting either of them hide the original exception.
		 */
		protected RuntimeException handleFailure(Exception e) {
			if(runningEngine==this) {
				runningEngine = null;
			}
			// A cancellation is reported as is, not wrapped as an error
			RuntimeException ex = e instanceof CancelException ce ? ce : JsonException.wrap(e);
			// Only roll back a transaction that is actually pending: the failure can happen
			// before the first one is started, or while a new one is being started
			if(transactionOpen) {
				transactionOpen = false;
				try {
					rollbackTransaction();
				} catch(Exception re) {
					ex.addSuppressed(re);
				}
			}
			if(!closed) {
				closed = true;
				try {
					close();
				} catch(Exception ce) {
					ex.addSuppressed(ce);
				}
			}
			return ex;
		}
		protected boolean usesTransactions() {
			return supportsTransaction() && getTransactionThreshold()>0;
		}
		protected void beginEngineTransaction() {
			if(usesTransactions()) {
				startTransaction();
				transactionOpen = true;
			}
		}
		protected void commitEngineTransaction() {
			if(transactionOpen) {
				// Once commit is called the transaction is no longer pending, even if it fails
				transactionOpen = false;
				commitTransaction();
			}
		}
		protected void saveTemporaryTransaction() {
    		if(usesTransactions()) {
    			transactionCount++;
    			if(transactionCount>=getTransactionThreshold()) {
    				commitEngineTransaction();
    				beginEngineTransaction();
    				transactionCount = 0;
    			}
    		}
		}
		/**
		 * Writes every content of the stream to the target, closing the stream.
		 */
		protected void processStream(Stream<JsonContent> stream, Consumer<JsonContent> processor) {
			try (Stream<JsonContent> content=stream) {
				// Sequential: the engine state (counts, transaction) is not thread safe
				content.sequential().forEach(c -> {
					processor.accept(c);
					saveTemporaryTransaction();
				});
			}
		}
		
	    public void saveJsonContent(ImportResult result, JsonContent content) {
	    	saveJsonContent(result, content, true);
	    }
	    /**
	     * Saves a content.
	     * @param count true if the content has to be counted as inserted or deleted.
	     * A content written to resolve a conflict is not, as it has already been
	     * counted as a conflict.
	     */
	    protected void saveJsonContent(ImportResult result, JsonContent content, boolean count) {
			if(cancel) {
				notify(JsonTargetImpl.Event.CANCEL, result.getProcessed(), result.getDeleted(), estimatedCount, 0);				
				throw new CancelException();
			}
			// A target that cannot delete ignores the deletions: they are neither written
			// nor counted as deleted
			if(content.getType()==TYPE.DELETION && !supportsDeletions()) {
				if(count && result instanceof ReplicationResult rr) {
					rr.addIgnored();
				}
				return;
			}
			long nowTS = System.currentTimeMillis();
			if( (nowTS-lastTS)>=notificationDelay ) {
				lastTS = nowTS;
				notify(JsonTargetImpl.Event.PROCESS, result.getProcessed(), result.getDeleted(), estimatedCount, nowTS-startTS);
			}
			
			// Inc the counts, what ever happens (even when beforeProcessing skips the content)
			if(count) {
				if(content.getType()==TYPE.DELETION) {
					result.addDeleted();
				} else {
					result.addInserted();
				}
			}

			JsonContent c = beforeProcessing(content);
			if(c!=null) {
				JsonTargetImpl.this.saveJsonContent(c);
				afterProcessing(c);
			}
		}

	    protected JsonContent beforeProcessing(JsonContent content) {
			if(beforeProcessing!=null) {
				return beforeProcessing.apply(content);
			}
			return content;
		}
		protected void afterProcessing(JsonContent content) {
			if(afterProcessing!=null) {
				afterProcessing.accept(content);
			}
		}

		protected void notify(JsonTargetImpl.Event event, long processed, long deleted, long estimatedCount, long ellapsedMs) {
			if(notification!=null) {
				notification.notify(event, processed, deleted, estimatedCount, ellapsedMs);
			}
		}
	}
	
	protected class ImportEngine extends Engine {
		
		protected JsonSource source;
		protected RangeFilter filter;
		
		protected ImportEngine(JsonSource source, RangeFilter filter) {
			this.source = source;
			this.filter = filter;
		}

		protected ImportResult importData() {
			try {
				initEngine();
				beginEngineTransaction();
				
				ImportResult result = new ImportResult();
				processStream(source.stream(filter), content -> saveJsonContent(result,content));

				commitEngineTransaction();
				
				closeEngine(result);
				
				return result;
			} catch(Exception e) {
				throw handleFailure(e);
			}
		}
	}

	
	//
	// Replication engine
	//
	
	protected class ReplicationEngine extends Engine {
		
		protected ReplicationSource source;
		protected ConflictResolver resolver;
		
		protected ReplicationEngine(ReplicationSource source, ConflictResolver resolver) {
			this.source = source;
			this.resolver = resolver;
		}

		protected ReplicationResult replicate() {
			long start = System.currentTimeMillis();
			String targetId = ((ReplicationTarget)JsonTargetImpl.this).getReplicationId();
			Instant lastReplication = replicationTable.lastReplication(source.getReplicationId(), targetId);
			RangeFilter rangeFilter = new RangeFilter(lastReplication, replicationTable.now());
			try {
				initEngine();
				beginEngineTransaction();
				
				ReplicationResult result = new ReplicationResult();
				result.setRangeFilter(rangeFilter);

				processStream(source.stream(rangeFilter), content -> replicateContent(content,rangeFilter.getSince(),result));
				long end = System.currentTimeMillis();
				result.setDuration(end-start);

				// The watermark is only saved once the target changes are committed: saving it
				// first would make a failed commit lose these changes for good, as the next
				// replication would start after them
				commitEngineTransaction();
				replicationTable.saveReplication(source.getReplicationId(), targetId, rangeFilter.getUntil(), result);

				closeEngine(result);
				return result;
			} catch(Exception e) {
				throw handleFailure(e);
			}
		}

		protected void replicateContent(JsonContent content, Instant lastRep, ReplicationResult result) {
			if(content.getType()==TYPE.DELETION && !supportsDeletions()) {
				// Not a conflict either: the target ignores the deletions
				result.addIgnored();
				return;
			}
			JsonKey key = content.getKey();
			
			// Check for a replication conflict
			// This happens when a changed content was after the last replication
			JsonContent target = readJsonContent(key);

			// We detect a potential conflict when the target was updated after the last replication
			// A target without a timestamp is treated as the oldest possible record
			if(target!=null && (lastRep==null || (target.getTimestamp()!=null && target.getTimestamp().compareTo(lastRep)>=0)) ) {
				if(target.getType()==TYPE.DELETION && content.getType()==TYPE.DELETION) {
					// Silently ignore the conflicts if both records where deleted
					result.addIgnored();
					return;
				}
				if(target.getType()==TYPE.RECORD && content.getType()==TYPE.RECORD) {
					// Silently ignore the conflicts if both records where inserted
					// And the values are the same
					if(JsonUtil.eq(content.getJson(), target.getJson())) {
						result.addIgnored();
						return;
					}
				}
				// Ok, there is a real conflict
				result.addConflict();
				if(resolver==null) {
					resolver = ConflictResolver.FAIL_EXCEPTION;
				}
				JsonContent[] c = resolver.resolve(content, target);
				if(c==null || c.length==0) {
					// Ok, ignore that content
					return;
				}
				for(int i=0; i<c.length; i++) {
					saveJsonContent(result, c[i], false);
				}
			} else {
				saveJsonContent(result, content);
			}
		}
	}

}
