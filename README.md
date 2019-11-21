## 左侧双Y轴的曲线对比图表的实现

### 一、设计图
![image.png](https://upload-images.jianshu.io/upload_images/9090596-b043043929f7934d.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

![image.png](https://upload-images.jianshu.io/upload_images/9090596-084f7824269c29da.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


思考:
    1.图形两种十分相似，就放一起写了，分为单双轴，x轴坐标数= 单条曲线坐标点数量;
    2.单轴共享y轴，也就意味着y轴的坐标点需要由两个曲线数据的最大值决定；
    2.双轴的图形分为两条y轴和一条x轴。y轴坐标由各自的最大值决定；
    3.双轴y轴有两种刻度，所以两条曲线是分别用各自的坐标系绘制上去的，只是共享了x轴；
    4.绘图需要用到贝赛尔曲线和填充渐变色;
    5.手势滚动和事件拦截相关逻辑。
   
   
### 二、绘制坐标系
创建坐标点列表
```Kotlin
    private var points = arrayListOf<PointF>()
    private var points2 = arrayListOf<PointF>()
```
```Kotlin
 override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        //计算y轴间隔
        yAxisSpace = (measuredHeight - yAxisBottomOffset) / yAxisCount.toFloat()
        //计算x轴间隔
        xAxisSpace = (measuredWidth - maxYAxisWidth - yAxisLeftOffset) / xAxisCount.toFloat()

        var tempX = maxYAxisWidth + yAxisLeftOffset
        var tempY = height.toFloat() - yAxisBottomOffset
        var tempY2 = tempY
        points.clear()
        points2.clear()
        //添加坐标原点
        points.add(PointF(tempX + scrollOffsetX, tempY))
        points2.add(PointF(tempX + scrollOffsetX, tempY2))
        xArray.forEachIndexed { index, _ ->
            //遍历x轴坐标，把坐标点分别计算添加到列表
            tempX += xAxisSpace
            tempY = OFFSET_CHART_TOP + (height.toFloat() - yAxisBottomOffset - OFFSET_CHART_TOP) * (1 - (yArray[index]) / if (isDouble) yArray.max()!! else maxValue) //yArray.max()!!
            tempY2 = OFFSET_CHART_TOP + (height.toFloat() - yAxisBottomOffset - OFFSET_CHART_TOP) * (1 - (yArray2[index]) / if (isDouble) yArray2.max()!! else maxValue) //yArray2.max()!!
            points.add(PointF(tempX + scrollOffsetX, tempY))
            points2.add(PointF(tempX + scrollOffsetX, tempY2))
        }
....
    }
```

````Kotlin
  override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        var yUsed = height.toFloat() - yAxisBottomOffset
        val xUsed = maxYAxisWidth + yAxisLeftOffset

        //x坐标
        textPaint.color = Color.BLACK
        textPaint.textAlign = Paint.Align.CENTER
        xArray.forEachIndexed { index, it ->
            canvas?.drawText(it, points[index + 1].x - scrollOffsetX, height.toFloat(), textPaint)
        }
        canvas?.drawLine(xUsed, yUsed, max(points.last().x + xUsed, width.toFloat()), yUsed, textPaint)

        //y坐标
        textPaint.textAlign = Paint.Align.RIGHT
        canvas?.drawLine(xUsed, yUsed, xUsed, 0f, textPaint)
        for (i in 0 until yAxisCount) {
        //如果是双Y轴则分别用各自最大值计算Y坐标，否则用两者最大值计算
            if (isDouble) {
                textPaint.color = color
                canvas?.drawText((i / yAxisCount.toFloat() * yArray.max()!!).toString(), maxYAxisWidth, yUsed, textPaint)
                textPaint.color = color2
                canvas?.drawText((i / yAxisCount.toFloat() * yArray2.max()!!).toString(), maxYAxisWidth - yArrayWordWidth, yUsed, textPaint)
            } else {
                canvas?.drawText((i / yAxisCount.toFloat() * maxValue).toString(), maxYAxisWidth, yUsed, textPaint)
            }
            yUsed -= yAxisSpace
        }
    }
````
坐标系便绘制好了
![坐标系](https://upload-images.jianshu.io/upload_images/9090596-dcbc6646809d9951.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

### 三、绘制坐曲线和坐标点
贝塞尔曲线分为二次(quadTo)和三次(cubicTo)贝塞尔曲线，这里需要把坐标连贯穿过，就选择三次贝塞尔曲线实现。
```
光滑曲线必定处处可导。
```
![示例图](https://upload-images.jianshu.io/upload_images/9090596-4461ebeed10a1861.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
A2和B1就是三次贝赛尔曲线的控制点，其连线是曲线的切线，切点为A3。其中，令A0和B3连线的斜率 = A2和B1连线的斜率，可以让曲线过度更自然。
那么
```
令
A0和B3连线的斜率 k = (B3Y - A0Y) / (B3X - A0X)
常数 b = A3Y - k * A3X
则
A2的X坐标 A2X = A3X - (A3X - A0X) * rate
A2的Y坐标 A2Y = k * A2X + b
B1的X坐标 B1X = A3X + (B3X - A3X) * rate
B1的Y坐标 B1Y = k * B1X + b
```
rate 一般取(0,0.5)。
除此以外，如果数值点是第一个点或者最后一个点，可以把斜率k视为0，然后只计算左控制点或者右控制点。

先看看rate去[0,1]对曲线的影响:
![rate对曲线的影响](https://upload-images.jianshu.io/upload_images/9090596-ea6cb40ba2edbe26.gif?imageMogr2/auto-orient/strip)
所以这个rate是当前点到两侧点的距离因子，如果大于0.5f会使曲度过大，导致曲线变形不利于观看。
#### 1.计算控制点坐标
````Kotlin
 /**
     * 计算控制点坐标
     * @param pointList 坐标点列表
     * @param controlPoints 控制点坐标(空列表)
     */
    private fun calculateControlPoint(pointList: List<PointF>, controlPoints: ArrayList<PointF>) {
        controlPoints.clear()
        if (pointList.size <= 1) {
            return
        }
        for ((i, point) in pointList.withIndex()) {
            when (i) {
                0 -> {//第一项
                    //添加后控制点
                    val nextPoint = pointList[i + 1]
                    val controlX = point.x + (nextPoint.x - point.x) * SMOOTHNESS
                    val controlY = point.y
                    controlPoints.add(PointF(controlX, controlY))
                }
                pointList.size - 1 -> {//最后一项
                    //添加前控制点
                    val lastPoint = pointList[i - 1]
                    val controlX = point.x - (point.x - lastPoint.x) * SMOOTHNESS
                    val controlY = point.y
                    controlPoints.add(PointF(controlX, controlY))
                }
                else -> {//中间项
                    val lastPoint = pointList[i - 1]
                    val nextPoint = pointList[i + 1]
                    val k = (nextPoint.y - lastPoint.y) / (nextPoint.x - lastPoint.x)
                    val b = point.y - k * point.x
                    //添加前控制点
                    val lastControlX = point.x - (point.x - lastPoint.x) * SMOOTHNESS
                    val lastControlY = k * lastControlX + b
                    controlPoints.add(PointF(lastControlX, lastControlY))
                    //添加后控制点
                    val nextControlX = point.x + (nextPoint.x - point.x) * SMOOTHNESS
                    val nextControlY = k * nextControlX + b
                    controlPoints.add(PointF(nextControlX, nextControlY))
                }
            }
        }
    }
````

```Kotlin
 /**
     * 绘制曲线、坐标以及填充渐变色
     */
    private fun Canvas.linkPath(path: Path, maxHeight: Float, points: ArrayList<PointF>, controlPoints: ArrayList<PointF>, paint: Paint, color: Int) {
        //连接各部分曲线
        path.reset()
        val firstPoint = points.first()
        path.moveTo(firstPoint.x - scrollOffsetX, firstPoint.y)
        path.lineTo(firstPoint.x - scrollOffsetX, firstPoint.y)
        for (i in 0 until ((points.size - 1) * 2) step 2) {
            val leftControlPoint = controlPoints[i]
            val rightControlPoint = controlPoints[i + 1]
            val rightPoint = points[i / 2 + 1]
            path.cubicTo(
                    leftControlPoint.x - scrollOffsetX,
                    leftControlPoint.y,
                    rightControlPoint.x - scrollOffsetX,
                    rightControlPoint.y,
                    rightPoint.x - scrollOffsetX, rightPoint.y)
        }
        val lastPoint = points.last()

        //填充渐变色
        path.lineTo(lastPoint.x - scrollOffsetX, lastPoint.y)
        path.lineTo(firstPoint.x - scrollOffsetX, maxHeight + OFFSET_CHART_TOP)
        paint.alpha = 130
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0F, 0F, 0F,
                maxHeight, color, Color.WHITE, Shader.TileMode.CLAMP)
        drawPath(path, paint)

        //绘制全部路径
        path.setLastPoint(lastPoint.x - scrollOffsetX, lastPoint.y)
        paint.strokeWidth = SIZE_LINE
        paint.style = Paint.Style.STROKE
        paint.shader = null
        paint.color = color
        drawPath(path, paint)

        for (i in 0 until points.size) {
            val point = points[i]
            //画数值点
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            paint.color = Color.WHITE
            drawCircle(point.x - scrollOffsetX, point.y, SIZE_POINT, paint)

            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            paint.color = color
            drawCircle(point.x - scrollOffsetX, point.y, SIZE_POINT, paint)
        }
    }
```
```Kotlin
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        var yUsed = height.toFloat() - yAxisBottomOffset
        val xUsed = maxYAxisWidth + yAxisLeftOffset

...
        linePaint.color = color
        canvas?.linkPath(
            path = linePath,
            maxHeight = yUsed - OFFSET_CHART,
            points = mPoints,
            controlPoints = mControlPointList,
            paint = linePaint,
            color = linePaint.color
        )

        linePaint.color = color2
        canvas?.linkPath(
            path = linePath,
            maxHeight = yUsed - OFFSET_CHART,
            points = mPoints2,
            controlPoints = mControlPointList2,
            paint = linePaint,
            color = linePaint.color
        )
...
}
```
绘制完成如下:
![绘制完成示例图](https://upload-images.jianshu.io/upload_images/9090596-4a81005dc1e86c06.jpg?imageMogr2/auto-orient/strip%7CimageView2/2/w/480)
### 四、添加手势滑动
此图只需要左右滑动，所以添加scrollOffsetX字段用于控制滚动偏移量。利用OverScroller和GestureDetector做一个可以回弹的滚动，如下：
```kotlin
  override fun onTouchEvent(event: MotionEvent?): Boolean {
        ...
        return gestureDetector.onTouchEvent(event)
    }

  inner class MyOnGestureListener : GestureDetector.OnGestureListener {

        private val maxOffset by lazy { points2.last().x.toInt() - (width - maxYAxisWidth.toInt()) }

        override fun onShowPress(e: MotionEvent?) {}

        override fun onSingleTapUp(e: MotionEvent?): Boolean = false

        override fun onDown(e: MotionEvent?): Boolean = true

        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(scrollOffsetX.toInt(), 0, - velocityX.toInt(), 0, 0,
                    maxOffset, 0, 0, 200, 0)
            ViewCompat.postOnAnimation(this@LineChart, scrollRunnable)
            return false
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
            scrollOffsetX += distanceX
            invalidate()
            return false
        }

        override fun onLongPress(e: MotionEvent?) {}
    }

    inner class MyScrollRunnable : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                scrollOffsetX = scroller.currX.toFloat()
                invalidate()
                ViewCompat.postOnAnimation(this@LineChart, scrollRunnable)
            }
        }
    }
```
弹性滑动添加完毕,处理应用出现的问题:
 1.向左滑动会导致曲线覆盖Y轴:
用clipRect在绘制曲线的时候剪切下来再绘制。
```kotlin
        canvas?.save()
        canvas?.clipRect(xUsed.toInt(), 0, width, height)
        linePaint.color = color
        canvas?.linkPath(
                path = linePath,
                maxHeight = yUsed - OFFSET_CHART_TOP,
                points = points,
                controlPoints = controlPointList,
                paint = linePaint,
                color = linePaint.color
        )

        linePaint.color = color2
        canvas?.linkPath(
                path = linePath,
                maxHeight = yUsed - OFFSET_CHART_TOP,
                points = points2,
                controlPoints = controlPointList2,
                paint = linePaint,
                color = linePaint.color
        )
        canvas?.restore()
```
2.嵌套ScrollView的滑动冲突:
当是我们需要处理的横向滚动时，让parent不拦截事件
```kotlin
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mDownX = event.x
                mDownY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(mDownY - event.y) < abs(mDownX - event.x))
                    parent.requestDisallowInterceptTouchEvent(true)
            }
        }
        return gestureDetector.onTouchEvent(event)
    }
```
3.滑动->停止->抬手导致不会弹问题:
添加手指抬起判断，手指抬起没有滑动的时候，让视图滚动(offset)到设定的边值界。
```kotlin
          MotionEvent.ACTION_UP -> {
                if (scrollOffsetX != 0f && !scroller.computeScrollOffset()) {
                    gestureListener.onFling(null, null, if (scrollOffsetX < 0f) -20f else 20f, 0f)
                }
            }
```
### 五、完整代码和完成效果
```kotlin
package com.zhtx.business.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.max

class LineChart : View {

    companion object{
        //平滑度
        const val SMOOTHNESS = 0.39f
        //描点大小
        const val SIZE_POINT = 15f
        //线条宽度
        const val SIZE_LINE = 10f
        //图表顶部偏移
        const val OFFSET_CHART_TOP = 60f

        fun Float.sp2px() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this, Resources.getSystem().displayMetrics)
        fun Float.dp2px() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, Resources.getSystem().displayMetrics)
    }
    //右轴坐标数量
    private val yAxisCount = 10
    //x轴坐标数量
    private val xAxisCount = 5
    //文本画笔
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    //y轴间隔
    private var yAxisSpace = 0f
    //x轴间隔
    private var xAxisSpace = 0f
    //是否是双Y轴
    private var isDouble = false
    //数据一
    private var yArray = arrayListOf<Float>()
    //数据二
    private var yArray2 = arrayListOf<Float>()
    //x轴坐标数据
    private var xArray = arrayListOf<String>()
    //y轴底部偏移量
    private var yAxisBottomOffset = 0f
    //x轴左侧偏移量
    private var yAxisLeftOffset = 10f

    private var maxYAxisWidth = 0f

    private val linePath = Path()

    private var controlPointList = arrayListOf<PointF>()
    private var controlPointList2 = arrayListOf<PointF>()

    private var points = arrayListOf<PointF>()
    private var points2 = arrayListOf<PointF>()

    private val color by lazy { Color.parseColor("#614DB6") }
    private val color2 by lazy { Color.parseColor("#FC5C7D") }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    //数组1的Y轴文字宽度
    private var yArrayWordWidth = 0f

    private var maxValue = 0f

    private val gestureListener by lazy { MyOnGestureListener() }
    private val gestureDetector by lazy { GestureDetector(context, gestureListener) }

    private val scroller by lazy { OverScroller(context, AccelerateDecelerateInterpolator()) }

    private var scrollOffsetX = 0f

    private val scrollRunnable = MyScrollRunnable()

    constructor(context: Context) : super(context)

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)

    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle)

    init {
        init()
    }

    private fun init() {
        maxValue = max(yArray.max() ?: 0f, yArray2.max() ?: 0f)
        textPaint.textSize = 12f.sp2px()
        textPaint.textAlign = Paint.Align.LEFT
        yArrayWordWidth = textPaint.measureText(yArray.max().toString())
        maxYAxisWidth = if (isDouble) yArrayWordWidth + textPaint.measureText(yArray2.max().toString()) else textPaint.measureText(maxValue.toString())
        textPaint.color = Color.BLACK
        textPaint.strokeWidth = 2f
        yAxisBottomOffset = textPaint.fontMetrics.bottom - textPaint.fontMetrics.top
        linePaint.apply {
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isDither = true
        }
    }

    fun setDataArray(xArrAy: ArrayList<String>, yArray: ArrayList<Float>, yArray2: ArrayList<Float>) {
        this.xArray = xArrAy
        this.yArray = yArray
        this.yArray2 = yArray2
        init()
        measureChild()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        measureChild()
    }

    private fun measureChild() {
        //计算y轴间隔
        yAxisSpace = (measuredHeight - yAxisBottomOffset) / yAxisCount.toFloat()
        //计算x轴间隔
        xAxisSpace = (measuredWidth - maxYAxisWidth - yAxisLeftOffset) / xAxisCount.toFloat()

        var tempX = maxYAxisWidth + yAxisLeftOffset
        var tempY = height.toFloat() - yAxisBottomOffset
        var tempY2 = tempY
        points.clear()
        points2.clear()
        //添加坐标原点
        points.add(PointF(tempX + scrollOffsetX, tempY))
        points2.add(PointF(tempX + scrollOffsetX, tempY2))
        //遍历x轴坐标，把坐标点分别计算添加到列表
        xArray.forEachIndexed { index, _ ->
            tempX += xAxisSpace
            tempY = OFFSET_CHART_TOP + (height.toFloat() - yAxisBottomOffset - OFFSET_CHART_TOP) * (1 - (yArray[index]) / if (isDouble) yArray.max()!! else maxValue) //yArray.max()!!
            tempY2 = OFFSET_CHART_TOP + (height.toFloat() - yAxisBottomOffset - OFFSET_CHART_TOP) * (1 - (yArray2[index]) / if (isDouble) yArray2.max()!! else maxValue) //yArray2.max()!!
            points.add(PointF(tempX + scrollOffsetX, tempY))
            points2.add(PointF(tempX + scrollOffsetX, tempY2))
        }
        //计算控制点
        calculateControlPoint(points, controlPointList)
        calculateControlPoint(points2, controlPointList2)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        var yUsed = height.toFloat() - yAxisBottomOffset
        val xUsed = maxYAxisWidth + yAxisLeftOffset

        canvas?.save()
        canvas?.clipRect(xUsed.toInt(), 0, width, height)
        linePaint.color = color
        canvas?.linkPath(
                path = linePath,
                maxHeight = yUsed - OFFSET_CHART_TOP,
                points = points,
                controlPoints = controlPointList,
                paint = linePaint,
                color = linePaint.color
        )

        linePaint.color = color2
        canvas?.linkPath(
                path = linePath,
                maxHeight = yUsed - OFFSET_CHART_TOP,
                points = points2,
                controlPoints = controlPointList2,
                paint = linePaint,
                color = linePaint.color
        )
        canvas?.restore()


        //x坐标
        textPaint.color = Color.BLACK
        textPaint.textAlign = Paint.Align.CENTER
        xArray.forEachIndexed { index, it ->
            canvas?.drawText(it, points[index + 1].x - scrollOffsetX, height.toFloat(), textPaint)
        }
        canvas?.drawLine(xUsed, yUsed, max(points.last().x + xUsed, width.toFloat()), yUsed, textPaint)

        //y坐标
        textPaint.textAlign = Paint.Align.RIGHT
        canvas?.drawLine(xUsed, yUsed, xUsed, 0f, textPaint)
        for (i in 0 until yAxisCount) {
            //如果是双Y轴则分别用各自最大值计算Y坐标，否则用两者最大值计算
            if (isDouble) {
                textPaint.color = color
                canvas?.drawText((i / yAxisCount.toFloat() * yArray.max()!!).toString(), maxYAxisWidth, yUsed, textPaint)
                textPaint.color = color2
                canvas?.drawText((i / yAxisCount.toFloat() * yArray2.max()!!).toString(), maxYAxisWidth - yArrayWordWidth, yUsed, textPaint)
            } else {
                canvas?.drawText((i / yAxisCount.toFloat() * maxValue).toString(), maxYAxisWidth, yUsed, textPaint)
            }
            yUsed -= yAxisSpace
        }
    }

    /**
     * 计算控制点坐标
     * @param pointList 坐标点列表
     * @param controlPoints 控制点坐标(空列表)
     */
    private fun calculateControlPoint(pointList: List<PointF>, controlPoints: ArrayList<PointF>) {
        controlPoints.clear()
        if (pointList.size <= 1) {
            return
        }
        for ((i, point) in pointList.withIndex()) {
            when (i) {
                0 -> {//第一项
                    //添加后控制点
                    val nextPoint = pointList[i + 1]
                    val controlX = point.x + (nextPoint.x - point.x) * SMOOTHNESS
                    val controlY = point.y
                    controlPoints.add(PointF(controlX, controlY))
                }
                pointList.size - 1 -> {//最后一项
                    //添加前控制点
                    val lastPoint = pointList[i - 1]
                    val controlX = point.x - (point.x - lastPoint.x) * SMOOTHNESS
                    val controlY = point.y
                    controlPoints.add(PointF(controlX, controlY))
                }
                else -> {//中间项
                    val lastPoint = pointList[i - 1]
                    val nextPoint = pointList[i + 1]
                    val k = (nextPoint.y - lastPoint.y) / (nextPoint.x - lastPoint.x)
                    val b = point.y - k * point.x
                    //添加前控制点
                    val lastControlX = point.x - (point.x - lastPoint.x) * SMOOTHNESS
                    val lastControlY = k * lastControlX + b
                    controlPoints.add(PointF(lastControlX, lastControlY))
                    //添加后控制点
                    val nextControlX = point.x + (nextPoint.x - point.x) * SMOOTHNESS
                    val nextControlY = k * nextControlX + b
                    controlPoints.add(PointF(nextControlX, nextControlY))
                }
            }
        }
    }

    /**
     * 绘制曲线、坐标以及填充渐变色
     */
    private fun Canvas.linkPath(path: Path, maxHeight: Float, points: ArrayList<PointF>, controlPoints: ArrayList<PointF>, paint: Paint, color: Int) {
        //连接各部分曲线
        path.reset()
        val firstPoint = points.first()
        path.moveTo(firstPoint.x - scrollOffsetX, firstPoint.y)
        path.lineTo(firstPoint.x - scrollOffsetX, firstPoint.y)
        for (i in 0 until ((points.size - 1) * 2) step 2) {
            val leftControlPoint = controlPoints[i]
            val rightControlPoint = controlPoints[i + 1]
            val rightPoint = points[i / 2 + 1]
            path.cubicTo(
                    leftControlPoint.x - scrollOffsetX,
                    leftControlPoint.y,
                    rightControlPoint.x - scrollOffsetX,
                    rightControlPoint.y,
                    rightPoint.x - scrollOffsetX, rightPoint.y)
        }
        val lastPoint = points.last()

        //填充渐变色
        path.lineTo(lastPoint.x - scrollOffsetX, lastPoint.y)
        path.lineTo(firstPoint.x - scrollOffsetX, maxHeight + OFFSET_CHART_TOP)
        paint.alpha = 130
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0F, 0F, 0F,
                maxHeight, color, Color.WHITE, Shader.TileMode.CLAMP)
        drawPath(path, paint)

        //绘制全部路径
        path.setLastPoint(lastPoint.x - scrollOffsetX, lastPoint.y)
        paint.strokeWidth = SIZE_LINE
        paint.style = Paint.Style.STROKE
        paint.shader = null
        paint.color = color
        drawPath(path, paint)

        for (i in 0 until points.size) {
            val point = points[i]
            //画数值点
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            paint.color = Color.WHITE
            drawCircle(point.x - scrollOffsetX, point.y, SIZE_POINT, paint)

            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            paint.color = color
            drawCircle(point.x - scrollOffsetX, point.y, SIZE_POINT, paint)
        }
    }


    private var mDownX = 0f
    private var mDownY = 0f

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mDownX = event.x
                mDownY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(mDownY - event.y) < abs(mDownX - event.x))
                    parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP -> {
                if (scrollOffsetX != 0f && !scroller.computeScrollOffset()) {
                    gestureListener.onFling(null, null, if (scrollOffsetX < 0f) -20f else 20f, 0f)
                }
            }
        }
        return gestureDetector.onTouchEvent(event)
    }

    inner class MyOnGestureListener : GestureDetector.OnGestureListener {

        private val maxOffset by lazy { points2.last().x.toInt() - (width - maxYAxisWidth.toInt()) }

        override fun onShowPress(e: MotionEvent?) {}

        override fun onSingleTapUp(e: MotionEvent?): Boolean = false

        override fun onDown(e: MotionEvent?): Boolean = true

        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            scroller.fling(scrollOffsetX.toInt(), 0, - velocityX.toInt(), 0, 0,
                    maxOffset, 0, 0, 200, 0)
            ViewCompat.postOnAnimation(this@LineChart, scrollRunnable)
            return false
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
            scrollOffsetX += distanceX
            invalidate()
            return false
        }

        override fun onLongPress(e: MotionEvent?) {}
    }

    inner class MyScrollRunnable : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                scrollOffsetX = scroller.currX.toFloat()
                invalidate()
                ViewCompat.postOnAnimation(this@LineChart, scrollRunnable)
            }
        }
    }
}
```
![完成效果](https://upload-images.jianshu.io/upload_images/9090596-0923cbe0ef2370ef.gif?imageMogr2/auto-orient/strip)

### 六、参考文档
[Android艺术之画一条平滑的曲线
]([https://www.jianshu.com/p/c4601bab860a](https://www.jianshu.com/p/c4601bab860a))
