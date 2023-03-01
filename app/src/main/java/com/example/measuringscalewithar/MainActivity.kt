package com.example.measuringscalewithar

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.measuringscalewithar.databinding.ActivityMainBinding
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import java.util.Hashtable
import kotlin.math.pow
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(), Scene.OnUpdateListener {
    lateinit var binding:ActivityMainBinding
    lateinit var arFragment: ArFragment
//    private var distanceModeTextView: TextView? = null
//    private lateinit var pointTextView: TextView
//
    var breakChain=false
    lateinit var cubeRenderable: ModelRenderable
    private var distanceCardRenderable: ViewRenderable? = null

    var placedAnchors=ArrayList<Anchor>()
    var placedAnchorNodes=ArrayList<AnchorNode>()
    val midAnchors: ArrayList<Anchor> = arrayListOf()
    val midAnchorNodes:ArrayList<AnchorNode> = arrayListOf()
    val distanceCardRenderables:ArrayList<ViewRenderable> = arrayListOf()
    val lengthDatas:ArrayList<String> = arrayListOf()
    val nodesForLine:ArrayList<Node> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        val link="https://firebasestorage.googleapis.com/v0/b/woodpeaker-39311.appspot.com/o/models%2Fscene.glb?alt=media&token=54bc56cd-2bfa-4f7a-912e-070b669b66ad"
//        val localLink="models/scene.glb"
        arFragment=(supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment)

//        arFragment.setOnTapPlaneGlbModel(localLink)
        initSphere()
        initDistanceCard()
        arFragment.apply {
            setOnSessionConfigurationListener { session, config ->
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.depthMode = Config.DepthMode.AUTOMATIC
                }
            }
            setOnViewCreatedListener { arSceneView ->
                // Available modes: DEPTH_OCCLUSION_DISABLED, DEPTH_OCCLUSION_ENABLED
                arSceneView.cameraStream.depthOcclusionMode =
                    CameraStream.DepthOcclusionMode.DEPTH_OCCLUSION_ENABLED
            }
        }


        arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            tapDistanceOf2Points(hitResult)
        }

        binding.clear.setOnClickListener(View.OnClickListener { clearAll() })
        binding.proceed.setOnClickListener(View.OnClickListener { processToNext()  })
        binding.removeLast.setOnClickListener(View.OnClickListener { removeLast() })

    }


    fun calculateDistance(objectPose0: Vector3, objectPose1: Vector3): Float{
        return calculateDistance(
            objectPose0.x - objectPose1.x,
            objectPose0.y - objectPose1.y,
            objectPose0.z - objectPose1.z
        )
    }

    fun calculateDistance(x: Float, y: Float, z: Float): Float {
        return sqrt(x.pow(2) + y.pow(2) + z.pow(2))
    }

    override fun onUpdate(frameTime: FrameTime?) {
        if(placedAnchorNodes.size>=2){
            var j=1
            while (j<placedAnchorNodes.size){
                if(placedAnchorNodes.size!=5) {
                    measureDistanceOf2Points(j - 1, j)
                    j++
                }
            }
        }
        Log.d("TAG","onUpdate....")
    }
    private fun drawLine(node1: AnchorNode, node2: AnchorNode) {
        //Draw a line between two AnchorNodes (adapted from https://stackoverflow.com/a/52816504/334402)
        Log.d("TAG", "drawLine")
        val point1: Vector3
        val point2: Vector3
        point1 = node1.worldPosition
        point2 = node2.worldPosition

        val difference = Vector3.subtract(point1, point2)
        val directionFromTopToBottom = difference.normalized()
        val rotationFromAToB: Quaternion =
            Quaternion.lookRotation(directionFromTopToBottom, Vector3.up())
        MaterialFactory.makeOpaqueWithColor(applicationContext, Color(0F, 255F, 244F))
            .thenAccept { material: Material? ->
                val model = ShapeFactory.makeCube(
                    Vector3(.01f, .01f, difference.length()),
                    Vector3.zero(), material
                )
                val lineAnchor = node2.anchor
                val nodeForLine = Node()
                nodeForLine.setParent(node1)
                nodeForLine.setRenderable(model)
                nodeForLine.setWorldPosition(Vector3.add(point1, point2).scaled(.5f))
                nodeForLine.setWorldRotation(rotationFromAToB)
                nodesForLine.add(nodeForLine)
            }
    }

    private fun initDistanceCard(){
        Log.d("TAG","fuck1")

        ViewRenderable.builder().setView(this, R.layout.distance_text_layout).build()
        .thenAccept{
            Log.d("TAG","fuck11")

            it.isShadowCaster = false
                it.isShadowReceiver = false
                distanceCardRenderables.add(it)
            Log.d("TAG","fuck111")
        }.exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null }

    }
    private fun initSphere() {
        MaterialFactory.makeTransparentWithColor(
            this,
            Color(android.graphics.Color.RED)
        )
            .thenAccept { material: Material? ->
                cubeRenderable = ShapeFactory.makeSphere(
                    0.02f,
                    Vector3.zero(),
                    material)
                cubeRenderable!!.setShadowCaster(false)
                cubeRenderable!!.setShadowReceiver(false)
            }
            .exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }
    }
    private fun tapDistanceOf2Points(hitResult: HitResult){
        placeAnchor(hitResult, cubeRenderable!!)
        if ((placedAnchorNodes.size > 1 && placedAnchorNodes.size<=4) ||
            placedAnchorNodes.size>5){
            val b=placedAnchorNodes.size-1
            val a=placedAnchorNodes.size-2
            val midPosition = floatArrayOf(
                (placedAnchorNodes[a].worldPosition.x + placedAnchorNodes[b].worldPosition.x) / 2,
                (placedAnchorNodes[a].worldPosition.y + placedAnchorNodes[b].worldPosition.y) / 2,
                (placedAnchorNodes[a].worldPosition.z + placedAnchorNodes[b].worldPosition.z) / 2)
            val quaternion = floatArrayOf(0.0f,0.0f,0.0f,0.0f)
            val pose = Pose(midPosition, quaternion)

            placeMidAnchor(pose, a,b)
        }
    }
    private fun placeAnchor(hitResult: HitResult,
                            renderable: ModelRenderable){
        val anchor = hitResult.createAnchor()
        placedAnchors.add(anchor)

        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(arFragment!!.arSceneView.scene)
        }
        placedAnchorNodes.add(anchorNode)

        val node = TransformableNode(arFragment!!.transformationSystem)
            .apply{
                this.rotationController.isEnabled = false
                this.scaleController.isEnabled = false
                this.translationController.isEnabled = true
                this.renderable = renderable
                setParent(anchorNode)
            }

        arFragment!!.arSceneView.scene.addOnUpdateListener(this)
        arFragment!!.arSceneView.scene.addChild(anchorNode)
        node.select()
    }
    private fun placeMidAnchor(pose: Pose,
                               a:Int,b:Int){
        val anchor = arFragment!!.arSceneView.session!!.createAnchor(pose)
        midAnchors.add(anchor)

        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(arFragment!!.arSceneView.scene)
        }
//        val distanceCardRenderable=initDistanceCard()
//        arrayListOf(anchorNode,this.distanceCardRenderable)?.let { midAnchorNodes.put(key, it) }
        val node = TransformableNode(arFragment!!.transformationSystem)
            .apply{
                this.rotationController.isEnabled = true
                this.scaleController.isEnabled = true
                this.translationController.isEnabled = true
                Log.d("TAG","fuck2")
                this.renderable = distanceCardRenderables.last()
                setParent(anchorNode)
            }
        initDistanceCard()
        midAnchorNodes.add(anchorNode)

        drawLine(placedAnchorNodes[a],placedAnchorNodes[b])
        arFragment!!.arSceneView.scene.addOnUpdateListener(this)
        arFragment!!.arSceneView.scene.addChild(anchorNode)
    }
    private fun clearAll(){
        placedAnchors.clear()
        for (anchorNode in placedAnchorNodes){
            arFragment!!.arSceneView.scene.removeChild(anchorNode)
            anchorNode.isEnabled = false
            anchorNode.anchor!!.detach()
            anchorNode.setParent(null)
        }
        placedAnchorNodes.clear()
        midAnchors.clear()
        for (arr in midAnchorNodes){
            arFragment!!.arSceneView.scene.removeChild(arr)
            arr.isEnabled = false
            arr.anchor!!.detach()
            arr.setParent(null)
        }
        midAnchorNodes.clear()
        distanceCardRenderables.clear()
        initDistanceCard()
        lengthDatas.clear()
    }
    fun removeLast(){
        if(placedAnchorNodes.isNotEmpty()) {
            placedAnchors.removeLast()
                val anchorNode=placedAnchorNodes.last()
                arFragment!!.arSceneView.scene.removeChild(anchorNode)
                anchorNode.isEnabled = false
                anchorNode.anchor!!.detach()
                anchorNode.setParent(null)
            placedAnchorNodes.removeLast()
            midAnchors.removeLast()
                val arr=midAnchorNodes.last()
                arFragment!!.arSceneView.scene.removeChild(arr)
                arr.isEnabled = false
                arr.anchor!!.detach()
                arr.setParent(null)
            midAnchorNodes.removeLast()
            distanceCardRenderables.removeAt(distanceCardRenderables.size - 2)
            if(lengthDatas.isNotEmpty()){
                lengthDatas.removeLast()
            }
        }
    }
    private fun measureDistanceOf2Points(i:Int,j:Int){
            val distanceMeter = calculateDistance(
                placedAnchorNodes[i].worldPosition,
                placedAnchorNodes[j].worldPosition)
            val distanceFt=makeDistanceTextWithFt(distanceMeter)
            lengthDatas.add(i,distanceFt)
            setDistanceToRenderable(distanceFt,distanceCardRenderables[j-1])
    }

    private fun setDistanceToRenderable(distance: String,distanceRenderable: ViewRenderable){
        val textView = (distanceRenderable.view as LinearLayout)
            .findViewById<TextView>(R.id.distanceCard)
        textView.text = distance
        Log.d("TAG", "distance: ${distance}")
    }
    private fun changeUnit(distanceMeter: Float, unit: String): Float{
        return when(unit){
            "cm" -> distanceMeter * 100
            "mm" -> distanceMeter * 1000
            "ft" -> distanceMeter * 3.28084F
            else -> distanceMeter
        }
    }
    private fun makeDistanceTextWithFt(distanceMeter: Float): String{
        val distanceFt = changeUnit(distanceMeter, "ft")
        val distanceFtFloor = "%.2f".format(distanceFt)
        return "${distanceFtFloor} ft"
    }
    fun processToNext(){

    }
}