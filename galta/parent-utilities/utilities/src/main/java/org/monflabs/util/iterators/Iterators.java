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
package org.monflabs.util.iterators;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.Predicate;

/**
 * 
 */
public final class Iterators {
	
	
	//
	// Iterator utilities
	//
	
	public static int size(Iterator<?> it) {
		int c = 0;
		while(it.hasNext()) {
			c++;
			it.next();
		}
		return c;
	}

	public static<T> T first(Iterator<T> it) {
		if(it.hasNext()) {
			return it.next();
		}
		return null;
	}

	public static<T> T last(Iterator<T> it) {
		T value = null;
		while(it.hasNext()) {
			value = it.next();
		}
		return value;
	}

	public static<T> T get(Iterator<T> it, int index) {
		for( ;index>0; index--) {
			if(it.hasNext()) {
				it.next();
			} else {
				return null;
			}
		}
		return first(it);
	}

	public static<T> List<T> collect(Iterator<T> it) {
		return collect(it,new ArrayList<T>());
	}

	public static<T> List<T> collect(Iterator<T> it, List<T> list) {
		while(it.hasNext()) {
			list.add(it.next());
		}
		return list;
	}

	public static<T> Object[] collectArray(Iterator<T> it) {
		List<T> l = collect(it);
		return l.toArray();
	}

	@SuppressWarnings("unchecked")
	public static<T> T[] collectArray(Iterator<T> it, Class<? extends T> t) {
		List<T> l = collect(it);
		return l.toArray((T[])Array.newInstance(t,l.size()));
	}

	
	//
	// Empty Iterator
	//
	private static Iterator<Object> emptyIterator = new Iterator<Object>() {
		@Override
		public boolean hasNext() {
			return false;
		}

		@Override
		public Object next() {
			throw new NoSuchElementException();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	};

	@SuppressWarnings("unchecked")
	public static <T> Iterator<T> empty() {
		return (Iterator<T>) emptyIterator;
	}

	

	//
	// Read only Iterators
	//
	public static <T> Iterator<T> readOnly(Iterator<T> it) {
		return new Iterator<T>() {
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}
			@Override
			public T next() {
				return it.next();
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	// Not that this iterator does not handle concurrent modification
	public static <T> Iterator<T> readOnlyList(List<T> list) {
		return new Iterator<T>() {
			private int current;

			@Override
			public boolean hasNext() {
				return current<list.size();
			}
			@Override
			public T next() {
				if (current<list.size()) {
					return list.get(current++);
				}
				throw new NoSuchElementException();
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	

	//
	// Single value Iterator
	//
	public static <T> Iterator<T> single(T value) {
		return new Iterator<T>() {
			private boolean done;

			@Override
			public boolean hasNext() {
				return !done;
			}

			@Override
			public T next() {
				if (!done) {
					done = true;
					return value;
				}
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	
	//
	// Static Iterator
	//
	@SafeVarargs
	public static <T> Iterator<T> staticValues(T...values) {
		return new Iterator<T>() {
			private int current;

			@Override
			public boolean hasNext() {
				return current<values.length;
			}

			@Override
			public T next() {
				if (current<values.length) {
					return values[current++];
				}
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}


	public static <T> Iterator<T> filter(Iterator<T> iterator, Predicate<T> filter) {
		return new Iterator<T>() {
			private boolean hascurrent = false;
			private T current;

			@Override
			public boolean hasNext() {
				if (hascurrent) {
					return true;
				}
				do {
					if (!iterator.hasNext()) {
						return false;
					}
					current = iterator.next();
				} while (!filter.test(current));
				hascurrent = true;
				return true;
			}

			@Override
			public T next() {
				if (hasNext()) {
					hascurrent = false;
					return current;
				}
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				if (hascurrent) {
					// hasNext() already advanced the underlying iterator past the element
					// returned by next(): removing now would remove the wrong element
					throw new IllegalStateException("remove() cannot be called after hasNext() on a filtered iterator");
				}
				iterator.remove();
			}
		};
	}
	// Sith an index
	public static <T> Iterator<T> filter(Iterator<T> iterator, IteratorPredicate<T> filter) {
		return new Iterator<T>() {
			private int index;
			private boolean hascurrent = false;
			private T current;

			@Override
			public boolean hasNext() {
				if (hascurrent) {
					return true;
				}
				do {
					if (!iterator.hasNext()) {
						return false;
					}
					current = iterator.next();
					index++;
				} while (!filter.test(current,index-1));
				hascurrent = true;
				return true;
			}

			@Override
			public T next() {
				if (hasNext()) {
					hascurrent = false;
					return current;
				}
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				if (hascurrent) {
					// hasNext() already advanced the underlying iterator past the element
					// returned by next(): removing now would remove the wrong element
					throw new IllegalStateException("remove() cannot be called after hasNext() on a filtered iterator");
				}
				iterator.remove();
			}
		};
	}
	
	public static <T> Iterator<T> skip(Iterator<T> iterator, int skip) {
		return new Iterator<T>() {
			private int count = skip;
			@Override
			public boolean hasNext() {
				if(count>0) {
					while(count>0 && iterator.hasNext() ) {
						count--;
						iterator.next();
					}
				}
				return iterator.hasNext();
			}

			@Override
			public T next() {
				if(hasNext()) {
					return iterator.next();
				}
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				iterator.remove();
			}
		};
	}
	
	public static <T> Iterator<T> limit(Iterator<T> iterator, int limit) {
		return new Iterator<T>() {
			private int left = limit;
			@Override
			public boolean hasNext() {
				if(left>0) {
					return iterator.hasNext();
				}
				return false;
			}

			@Override
			public T next() {
				if(hasNext()) {
					left--;
					return iterator.next();
				}
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				iterator.remove();
			}
		};
	}


	//
	// Aggregating Iterator
	//
	@SuppressWarnings("unchecked")
	public static <T> Iterator<T> concat(Iterator<T> t1, Iterator<T> t2) {
		if(t1!=null) {
			if(t2!=null) {
				return concat(new Iterator[] {t1,t2});
			} else {
				return t1;
			}
		}
		if(t2!=null) {
			return t2;
		}
		return empty();
	}
	@SuppressWarnings("unchecked")
	public static <T> Iterator<T> concat(Iterator<T> t1, Iterator<T> t2, Iterator<T> t3) {
		if(t1==null) {
			return concat(t2,t3);
		}
		if(t2==null) {
			return concat(t1,t3);
		}
		if(t3==null) {
			return concat(t1,t2);
		}
		return concat(new Iterator[] {t1,t2,t3});
	}
	@SuppressWarnings("unchecked")
	public static <T> Iterator<T> concat(Iterator<T> t1, Iterator<T> t2, Iterator<T> t3, Iterator<T> t4) {
		if(t1==null) {
			return concat(t2,t3,t4);
		}
		if(t2==null) {
			return concat(t1,t3,t4);
		}
		if(t3==null) {
			return concat(t1,t2,t4);
		}
		if(t4==null) {
			return concat(t1,t2,t3);
		}
		return concat(new Iterator[] {t1,t2,t3,t4});
	}
	@SuppressWarnings("unchecked")
	@SafeVarargs
	public static <T> Iterator<T> concat(Iterator<T>... iterators) {
		Iterator<T> single = null;
		for(int i=0; i<iterators.length; i++) {
			Iterator<T> it = iterators[i];
			if(it!=null) {
				if(single!=null) {
					return new Iterator<T>() {
						private int ptr = 0;
						private Iterator<T> current;
						{
							nextIterator();
						}
						private void nextIterator() {
							current = null;
							while(current==null && ptr<iterators.length) {
								current = iterators[ptr++];
							}
						}

						@Override
						public boolean hasNext() {
							while (current != null) {
								if (current.hasNext()) {
									return true;
								}
								nextIterator();
							}
							return false;
						}

						private Iterator<T> last;

						@Override
						public T next() {
							while (current != null) {
								if (current.hasNext()) {
									last = current;
									return current.next();
								}
								nextIterator();
							}
							throw new NoSuchElementException();
						}

						@Override
						public void remove() {
							if (last == null) {
								throw new IllegalStateException();
							}
							last.remove();
						}
					};
				}
				single = it;
			}
		}
		return single!=null ? single : (Iterator<T>)emptyIterator;
	}
	public static <T> Iterator<T> concat(Iterator<Iterator<T>> iterators) {
		return new Iterator<T>() {
			private Iterator<T> current;
			{
				nextIterator();
			}
			private void nextIterator() {
				current = null;
				while(current==null && iterators.hasNext()) {
					current = iterators.next();
				}
			}

			@Override
			public boolean hasNext() {
				while (current != null) {
					if (current.hasNext()) {
						return true;
					}
					nextIterator();
				}
				return false;
			}

			private Iterator<T> last;

			@Override
			public T next() {
				while (current != null) {
					if (current.hasNext()) {
						last = current;
						return current.next();
					}
					nextIterator();
				}
				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				if (last == null) {
					throw new IllegalStateException();
				}
				last.remove();
			}
		};
	}


	//
	// Mapping Iterator
	//
	public static <T, R> Iterator<R> map(Iterator<T> it, Function<T, R> mapper) {
		return new Iterator<R>() {
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public R next() {
				return mapper.apply(it.next());
			}

			@Override
			public void remove() {
				it.remove();
			}
		};
	}
	public static <T, R> Iterator<R> map(Iterator<T> it, IteratorMap<T, R> mapper) {
		return new Iterator<R>() {
			int index;
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public R next() {
				return mapper.map(it.next(),index++);
			}

			@Override
			public void remove() {
				it.remove();
			}
		};
	}


	//
	// Browse each entry of an iterator
	//
	public static <T> void forEach(Iterator<T> it, IteratorConsumer<T> consumer) {
		for( int i=0; it.hasNext(); i++) {
			T v = it.next();
			consumer.accept(v,i);
		}
	}

	
	//
	// Look for a specifc item
	//
	public static <T> T find(Iterator<T> it, IteratorPredicate<T> consumer, T notFoundValue) {
		for( int i=0; it.hasNext(); i++) {
			T v = it.next();
			boolean r = consumer.test(v,i);
			if(r) {
				return v;
			}
		}
		return notFoundValue;
	}

	
	//
	// Browse each entry of an iterator until it fails
	//
	public static <T> boolean every(Iterator<T> it, IteratorPredicate<T> consumer) {
		for( int i=0; it.hasNext(); i++) {
			T v = it.next();
			boolean r = consumer.test(v,i);
			if(!r) {
				return false;
			}
		}
		return true;
	}

	
	//
	// Reduce an iterator
	//
	public static <T,R> R reduce(Iterator<T> it, IteratorReduce<T,R> reducer, R initialValue) {
		for( int i=0; it.hasNext(); i++) {
			T v = it.next();
			initialValue = reducer.apply(initialValue,v,i);
		}
		return initialValue;
	}

	//
	// Browse each entry of an iterator until it fails
	//
	public static <T> boolean some(Iterator<T> it, IteratorPredicate<T> consumer) {
		for( int i=0; it.hasNext(); i++) {
			T v = it.next();
			boolean r = consumer.test(v,i);
			if(r) {
				return true;
			}
		}
		return false;
	}

	
	//
	// Array iterator
	//
	@SuppressWarnings("unchecked")
	public static <T> Iterator<T> array(T... array) {
        return new Iterator<T>() {
            private int i = 0;
            @Override
            public boolean hasNext() {
                return i<array.length;
            }
            @Override
            public T next() {
            	if(i<array.length) {
            		return array[i++];
            	}
				throw new NoSuchElementException();
            }
        };
	}
	public static Iterator<Object> arrayReflection(Object array) {
        return new Iterator<Object>() {
            private int current = 0;
            private int length = Array.getLength(array);
            @Override
            public boolean hasNext() {
    	        return current<length;
            }
    	    @Override
    		public Object next() {
    	        if( current<length ) {
    	            return Array.get(array, current++);
    	        }
    	        throw new NoSuchElementException( "No more elements in the iterator" );
    	    }
    	    @Override
    		public void remove() {
    	        throw new UnsupportedOperationException( "Cannot remove items on array iterator" );
    	    }
        };
	}
	
	//
	// Enumeration iterator
	//
	public static <T> Iterator<T> enumeration(Enumeration<T> en) {
        return new Iterator<T>() {
    	    @Override
    		public boolean hasNext() {
    	        return en.hasMoreElements();
    	    }
    	    @Override
    		public T next() {
    	        return en.nextElement();
    	    }

    	    @Override
    		public void remove() {
    	        throw new UnsupportedOperationException();
    	    }
        };
	}
	
	//
	// CharSequence Iterator
	//
	public static CharIterator charSequence(CharSequence cs) {
		return charSequence(cs,0,cs.length());
	}
	public static CharIterator charSequence(CharSequence cs, int start, int end) {
		return new CharIterator() {
			private int current = start;
			@Override
			public boolean hasNext() {
				return current<end;
			}
			@Override
			public char next() {
				if (current<end) {
					return cs.charAt(current++);
				}
				throw new NoSuchElementException();
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	//
	// Integer sequence Iterators
	//
	public static IntIterator intSequence(int start, int end) {
		return new IntIterator() {
			private int current = start;
			@Override
			public boolean hasNext() {
				return current<end;
			}
			@Override
			public int next() {
				if (current<end) {
					return current++;
				}
				throw new NoSuchElementException();
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	public static <R> Iterator<R> map(IntIterator it, IntFunction<R> mapper) {
		return new Iterator<R>() {
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}
			@Override
			public R next() {
				return mapper.apply(it.next());
			}
			@Override
			public void remove() {
				it.remove();
			}
		};
	}
	
	
	public static LongIterator longSequence(long start, long end) {
		return new LongIterator() {
			private long current = start;
			@Override
			public boolean hasNext() {
				return current<end;
			}
			@Override
			public long next() {
				if (current<end) {
					return current++;
				}
				throw new NoSuchElementException();
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	public static <R> Iterator<R> map(LongIterator it, LongFunction<R> mapper) {
		return new Iterator<R>() {
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public R next() {
				return mapper.apply(it.next());
			}

			@Override
			public void remove() {
				it.remove();
			}
		};
	}

	
	//
	// Nested iterator
	//
	@SuppressWarnings("unchecked")
	public static <T, R, I1> Iterator<R> nested(Iterator<I1> it, Function<I1, Iterator<?>> f1) {
		return new NestedIterator<R>(it, new Function[] {f1});
	}
	@SuppressWarnings("unchecked")
	public static <T, R, I1, I2> Iterator<R> nested(Iterator<I1> it, Function<I1, Iterator<I2>> f1, Function<I2, Iterator<?>> f2) {
		return new NestedIterator<R>(it, new Function[] {f1,f2});
	}
	private static class NestedIterator<T> implements Iterator<T> {

		private List<Iterator<?>> iteratorStack = new ArrayList<Iterator<?>>();
		private Function<Object, Iterator<?>>[] factories;

		private T next;
		private boolean hasNextValue;

		NestedIterator(Iterator<?> it, Function<Object, Iterator<?>>[] factories) {
			this.iteratorStack.add(it);
			this.factories = factories;
			hasNextValue = moveToNext();
		}

		@SuppressWarnings("unchecked")
		private boolean moveToNext() {
			while (!iteratorStack.isEmpty()) {
				int size = iteratorStack.size();
				Iterator<?> topIt = iteratorStack.get(size-1);
				if (!topIt.hasNext()) {
					iteratorStack.remove(size-1);
					continue;
				}
				Object nextValue = topIt.next();
				if(size<=factories.length) {
					Function<Object, Iterator<?>> factory = factories[size-1];
					Iterator<?> it = factory.apply(nextValue);
					iteratorStack.add(it);
			        continue;
			    } else {
			    	next = (T)nextValue;
			    	return true;
			    }
			}
			return false;
		}

		@Override
		public boolean hasNext() {
			if (!hasNextValue) {
				hasNextValue = moveToNext();
			}
			return hasNextValue;
		}

		@Override
		public T next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			hasNextValue = false;
			return next;
		}
	}
	

	//
	// Flattened iterator
	//
	public static <T> Iterator<T> flatten(Iterator<T> it, Function<T, Object> factory) {
		return new FlattenedIterator<T>(it, factory);
	}	
	private static class FlattenedIterator<T> implements Iterator<T> {

		private List<Iterator<T>> iteratorStack = new ArrayList<Iterator<T>>();
		private Function<T,Object> factory;

		private T next;
		private boolean hasNextValue;
		private boolean started;

		FlattenedIterator(Iterator<T> it, Function<T,Object> factory) {
			this.iteratorStack.add(it);
			this.factory = factory;
			// The first moveToNext() is deferred to the first hasNext()/
			// next() call (see hasNext() below) rather than run here -
			// probing the underlying source at CONSTRUCTION time, before
			// the caller has asked for anything, is observably wrong (an
			// unwanted extra call into the source) and can hang forever if
			// the source is infinite and every element maps to an empty
			// nested iterable (there would be no element to ever return,
			// but nothing has actually been REQUESTED yet either).
		}

		@SuppressWarnings("unchecked")
		private boolean moveToNext() {
			while (!iteratorStack.isEmpty()) {
				int size = iteratorStack.size();
				Iterator<T> topIt = iteratorStack.get(size-1);
				if (!topIt.hasNext()) {
					iteratorStack.remove(size-1);
					continue;
				}
				Object nextValue = topIt.next();
				if(factory!=null && !(nextValue instanceof Iterator<?>)) {
					nextValue = factory.apply((T)nextValue);
				}
				if(nextValue instanceof Iterator<?>) {
					iteratorStack.add((Iterator<T>)nextValue);
			        continue;
			    } else {
			    	next = (T)nextValue;
			    	return true;
			    }
			}
			next = null;
			return false;
		}

		@Override
		public boolean hasNext() {
			if (!started) {
				started = true;
				hasNextValue = moveToNext();
			} else if (!hasNextValue) {
				hasNextValue = moveToNext();
			}
			return hasNextValue;
		}

		@Override
		public T next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			hasNextValue = false;
			return next;
		}
	}
}
