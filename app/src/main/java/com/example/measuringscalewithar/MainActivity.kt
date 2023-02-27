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
import kotlin.math.pow
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(), Scene.OnUpdateListener {
    lateinit var binding:ActivityMainBinding
    lateinit var arFragment: ArFragment
    private val MIN_OPENGL_VERSION = 3.0


//    private var distanceModeTextView: TextView? = null
//    private lateinit var pointTextView: TextView
//
    private var cubeRenderable: ModelRenderable? = null

    var placedAnchors=ArrayList<Anchor>()
    var placedAnchorNodes=ArrayList<AnchorNode>()
    private val midAnchors: MutableMap<String, Anchor> = mutableMapOf()
    private val midAnchorNodes: MutableMap<String, AnchorNode> = mutableMapOf()
//
//    private val multipleDistances = Array(Constants.maxNumMultiplePoints,
//        {Array<TextView?>(Constants.maxNumMultiplePoints){null} })
    private lateinit var initCM: String
//
//    private lateinit var clearButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        val link="https://firebasestorage.googleapis.com/v0/b/woodpeaker-39311.appspot.com/o/models%2Fscene.glb?alt=media&token=54bc56cd-2bfa-4f7a-912e-070b669b66ad"
//        val localLink="models/scene.glb"
        arFragment=(supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment)
//        arFragment.setOnTapPlaneGlbModel(localLink)
        cubeRenderable=initSphere()
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
    }
    private fun placeAnchor(hitResult: HitResult,
                            renderable: Renderable){
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
//        for(i in midAnchorNodes.keys){
//            val rotationFromAToB: Quaternion =
//
//            midAnchorNodes.get(i).worldRotation=rotationFromAToB
//        }
//        arFragment.arSceneView.rotationX
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
        for(i in 0 until (placedAnchorNodes.size-1)){
            measureDistanceOf2Points(i,i+1)

        }
    }
    private fun drawLine(node1: AnchorNode, node2: AnchorNode) {
        //Draw a line between two AnchorNodes (adapted from https://stackoverflow.com/a/52816504/334402)
        Log.d("TAG", "drawLine")
        val point1: Vector3
        val point2: Vector3
        point1 = node1.worldPosition
        point2 = node2.worldPosition


        //First, find the vector extending between the two points and define a look rotation
        //in terms of this Vector.
        val difference = Vector3.subtract(point1, point2)
        val directionFromTopToBottom = difference.normalized()
        val rotationFromAToB: Quaternion =
            Quaternion.lookRotation(directionFromTopToBottom, Vector3.up())
        MaterialFactory.makeOpaqueWithColor(applicationContext, Color(0F, 255F, 244F))
            .thenAccept { material: Material? ->
                /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
               to extend to the necessary length.  */Log.d(
                "TAG",
                "drawLine insie .thenAccept"
            )
                val model = ShapeFactory.makeCube(
                    Vector3(.01f, .01f, difference.length()),
                    Vector3.zero(), material
                )
                /* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
           the midpoint between the given points . */
                val lineAnchor = node2.anchor
                val nodeForLine = Node()
                nodeForLine.setParent(node1)
                nodeForLine.setRenderable(model)
                nodeForLine.setWorldPosition(Vector3.add(point1, point2).scaled(.5f))
                nodeForLine.setWorldRotation(rotationFromAToB)
            }
    }

    private fun initDistanceCard():ViewRenderable?{
        var distanceCardViewRenderable:ViewRenderable?=null
        ViewRenderable
            .builder()
            .setView(this, R.layout.distance_text_layout)
            .build()
            .thenAccept{
                distanceCardViewRenderable = it
                distanceCardViewRenderable!!.isShadowCaster = false
                distanceCardViewRenderable!!.isShadowReceiver = false
            }
            .exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }
        return distanceCardViewRenderable
    }
    private fun initSphere():ModelRenderable? {
        var cubeRenderable: ModelRenderable?=null
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
        return cubeRenderable
    }
    private fun tapDistanceOf2Points(hitResult: HitResult){
        placeAnchor(hitResult, cubeRenderable!!)
        if (placedAnchorNodes.size > 1){
            val b=placedAnchorNodes.size-1
            val a=placedAnchorNodes.size-2
            val midPosition = floatArrayOf(
                (placedAnchorNodes[a].worldPosition.x + placedAnchorNodes[b].worldPosition.x) / 2,
                (placedAnchorNodes[a].worldPosition.y + placedAnchorNodes[b].worldPosition.y) / 2,
                (placedAnchorNodes[a].worldPosition.z + placedAnchorNodes[b].worldPosition.z) / 2)
            val quaternion = floatArrayOf(0.0f,0.0f,0.0f,0.0f)
            val pose = Pose(midPosition, quaternion)

            placeMidAnchor(pose)
        }
    }

    private fun placeMidAnchor(pose: Pose,
                               between: Array<Int> = arrayOf(0,1)){
        val midKey = "${between[0]}_${between[1]}"
        val anchor = arFragment!!.arSceneView.session!!.createAnchor(pose)
        midAnchors.put(midKey, anchor)

        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(arFragment!!.arSceneView.scene)
        }
        midAnchorNodes.put(midKey, anchorNode)
        val distanceCardRenderable=initDistanceCard()
        val node = TransformableNode(arFragment!!.transformationSystem)
            .apply{
                this.rotationController.isEnabled = true
                this.scaleController.isEnabled = true
                this.translationController.isEnabled = true
                this.renderable = distanceCardRenderable
                setParent(anchorNode)
            }
        arFragment!!.arSceneView.scene.addOnUpdateListener(this)
        arFragment!!.arSceneView.scene.addChild(anchorNode)
    }
    private fun clearAllAnchors(){
        placedAnchors.clear()
        for (anchorNode in placedAnchorNodes){
            arFragment!!.arSceneView.scene.removeChild(anchorNode)
            anchorNode.isEnabled = false
            anchorNode.anchor!!.detach()
            anchorNode.setParent(null)
        }
        placedAnchorNodes.clear()
        midAnchors.clear()
        for ((k,anchorNode) in midAnchorNodes){
            arFragment!!.arSceneView.scene.removeChild(anchorNode)
            anchorNode.isEnabled = false
            anchorNode.anchor!!.detach()
            anchorNode.setParent(null)
        }
        midAnchorNodes.clear()
    }
    private fun measureDistanceOf2Points(i:Int,j:Int){
        if (placedAnchorNodes.size >= 2) {
            val distanceMeter = calculateDistance(
                placedAnchorNodes[i].worldPosition,
                placedAnchorNodes[j].worldPosition)
            measureDistanceOf2Points(distanceMeter)
        }
    }

    private fun measureDistanceOf2Points(distanceMeter: Float){
        val distanceTextFt = makeDistanceTextWithFt(distanceMeter)
        val textView = (distanceCardViewRenderable!!.view as LinearLayout)
            .findViewById<TextView>(R.id.distanceCard)
        textView.text = distanceTextFt
        Log.d("TAG", "distance: ${distanceTextFt}")
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
}