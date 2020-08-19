/*
 * *************************************************************************
 *  AutoFitRecyclerView.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewStub
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.qh.mplayer.utils.LogUtils
import kotlin.math.abs
import kotlin.properties.Delegates

class AutoFitRecyclerView : RecyclerView {


    private var gridLayoutManager: GridLayoutManager? = null
    var columnWidth = -1
    private var spanCount = -1

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        if (attrs != null) {
            val attrsArray = intArrayOf(android.R.attr.columnWidth)
            val array = context.obtainStyledAttributes(attrs, attrsArray)
            columnWidth = array.getDimensionPixelSize(0, -1)
            array.recycle()
        }

        gridLayoutManager = GridLayoutManager(getContext(), 1)
        layoutManager = gridLayoutManager
        touchSlop=ViewConfiguration.get(context).scaledEdgeSlop
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (spanCount == -1 && columnWidth > 0) {
            val ratio = measuredWidth / columnWidth
            val spanCount = Math.max(1, ratio)
            gridLayoutManager!!.spanCount = spanCount
        } else
            gridLayoutManager!!.spanCount = spanCount

    }

    fun getPerfectColumnWidth(columnWidth: Int, margin: Int): Int {

        val wm = context.applicationContext.getSystemService<WindowManager>()!!
        val display = wm.defaultDisplay
        val displayWidth = display.width - margin

        val remainingSpace = displayWidth % columnWidth
        val ratio = displayWidth / columnWidth
        val spanCount = Math.max(1, ratio)

        return columnWidth + remainingSpace / spanCount
    }

    fun setNumColumns(spanCount: Int) {
        this.spanCount = spanCount
    }



    var lastX =0.0f
    var lastY =0.0f
    var touchSlop by Delegates.notNull<Int>()

    override fun setScrollingTouchSlop(slopConstant: Int) {
        super.setScrollingTouchSlop(slopConstant)
        var vc=ViewConfiguration.get(context)
        when(slopConstant)
        {
            TOUCH_SLOP_DEFAULT->touchSlop=vc.scaledTouchSlop
            TOUCH_SLOP_PAGING->touchSlop=ViewConfigurationCompat.getScaledPagingTouchSlop(vc)
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent?): Boolean {
        if (e==null) return false

        var intercept=false;
        when(e?.action)
        {
            MotionEvent.ACTION_DOWN->{
                lastX=e.x
                lastY=e.y
                return super.onInterceptTouchEvent(e)
            }
            MotionEvent.ACTION_MOVE->{
              if(scrollState!= SCROLL_STATE_DRAGGING){
                  val dx=x-lastX
                  val dy=y-lastY
                  var startScroll=false
                  if(Math.abs(dx)>touchSlop&&Math.abs(dx)>Math.abs(dy)){//layoutManager?.canScrollHorizontally()!! &&
                      //水平滑动
                      LogUtils.loge("=======水平滑动")
                      startScroll=true
                      if(dx>0)//向右面滑动
                      {
                          onSlideListener!!.rightSlide()
                      }
                      else{
                          onSlideListener!!.leftSlide()
                      }
                  }
                  if(layoutManager?.canScrollVertically()!!&&Math.abs(dy)>touchSlop&&Math.abs(dx)<Math.abs(dy))
                  {
                      //竖直方向的滑动
                      LogUtils.loge("=======竖直方向的滑动")
                      startScroll=true
                  }
                  return startScroll&&super.onInterceptTouchEvent(e)
              }
                return super.onInterceptTouchEvent(e)
            }
            else->super.onInterceptTouchEvent(e)
        }
        return intercept
    }



    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return super.dispatchTouchEvent(ev)
    }


    public interface OnSlideListener{
        fun leftSlide():Unit
        fun rightSlide():Unit
    }

    public  var onSlideListener:OnSlideListener?=null
}
