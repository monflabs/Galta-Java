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
package org.monflabs.util.sort;

public class QuickSort {
	
	public interface Accessor {
	    public abstract int size();
	    public abstract void exchange(int idx1, int idx2);
	    public abstract int compare(int idx1, int idx2);
	}
	
    public static void sort(Accessor accessor) {
    	doSort(accessor, 0, accessor.size());
    }

    public static void sort(Accessor accessor, int offset, int length) {
    	doSort(accessor, offset, length );
    }


    @SuppressWarnings("unused")
	private static final void doSort( Accessor accessor, int pivotP, int length ) {
        int    leftP, rightP, pivotEnd, pivotTemp, leftTemp;
        int    lNum;
        int    retval;

        tailRecursion:
        do { // Tail recursion

            // Shell sort on smallest array
            if( length<=7 ) {
                // Specific case of 2 or less items
                if(length == 2) {
                    if(accessor.compare(pivotP, rightP = 1 + pivotP) > 0) {
                    	accessor.exchange (pivotP, rightP);
                    }
                    return;
                }

                // Shell sort
                for( int i=0; i<length; i++ ) {
                    for( int j=i+1; j<length; j++ ) {
                        if( accessor.compare(i+pivotP, j+pivotP)>0 ) {
                        	accessor.exchange(i+pivotP,j+pivotP);
                        }
                    }
                }
                return;
            }

            rightP = (length - 1) + pivotP;
            leftP  = (length >> 1) + pivotP;

            // sort the pivot, left, and right elements for "median of 3"
            if(accessor.compare(leftP, rightP) > 0) {
            	accessor.exchange (leftP, rightP);
            }
            if(accessor.compare(leftP, pivotP) > 0) {
            	accessor.exchange (leftP, pivotP);
            } else {
                if(accessor.compare(pivotP, rightP) > 0) {
                	accessor.exchange (pivotP, rightP);
                }
            }

            if(length == 3) {
            	accessor.exchange (pivotP, leftP);
                return;
            }

            // now for the classic Hoare algorithm
            leftP = pivotEnd = pivotP + 1;

            mainloop:
            do {
                while((retval = accessor.compare(leftP, pivotP)) <= 0) {
                    if(retval == 0) {
                    	accessor.exchange(leftP, pivotEnd);
                        pivotEnd += 1;
                    }
                    if( leftP<rightP )
                        leftP += 1;
                    else
                        break mainloop;
                }

                while(leftP<rightP) {
                    if((retval = accessor.compare(pivotP, rightP)) < 0)
                        rightP -= 1;
                    else {
                    	accessor.exchange(leftP, rightP);
                        if(retval != 0) {
                            leftP += 1;
                            rightP -= 1;
                        }
                        break;
                    }
                }
            } while(leftP<rightP);

            if(accessor.compare(leftP, pivotP) <= 0) {
                leftP = leftP + 1;
            }
            leftTemp = leftP - 1;

            pivotTemp = pivotP;

            while((pivotTemp < pivotEnd) && (leftTemp >= pivotEnd)) {
            	accessor.exchange(pivotTemp, leftTemp);
                pivotTemp += 1;
                leftTemp -= 1;
            }

            lNum = leftP - pivotEnd;
            length = (length + pivotP) - leftP;

            // Sort smaller partition first to reduce stack usage
            if(length < lNum) {
                doSort(accessor,leftP, length);
                length = lNum;
            } else {
            	doSort(accessor,pivotP, lNum);
                pivotP = leftP;
            }
        } while(true); //goto tailRecursion;
    }
}