/*$Id: $
 --------------------------------------
  Skybility
 ---------------------------------------
  Copyright By Skybility ,All right Reserved
 * author   date   comment
 * jeremy  2012-8-2  Created
*/ 
package com.skybility.cloudsoft.rtunnel.listener; 
 
public interface EventListener<E> {
	public void handleEvent(E e);
}
