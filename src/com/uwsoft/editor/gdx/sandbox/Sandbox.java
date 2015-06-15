/*
 * ******************************************************************************
 *  * Copyright 2015 See AUTHORS file.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *****************************************************************************
 */

package com.uwsoft.editor.gdx.sandbox;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.uwsoft.editor.Overlap2D;
import com.uwsoft.editor.data.vo.ProjectVO;
import com.uwsoft.editor.gdx.actors.basic.PixelRect;
import com.uwsoft.editor.gdx.mediators.ItemControlMediator;
import com.uwsoft.editor.gdx.mediators.SceneControlMediator;
import com.uwsoft.editor.mvc.Overlap2DFacade;
import com.uwsoft.editor.mvc.proxy.ProjectManager;
import com.uwsoft.editor.mvc.proxy.ResolutionManager;
import com.uwsoft.editor.mvc.proxy.ResourceManager;
import com.uwsoft.editor.mvc.proxy.SceneDataManager;
import com.uwsoft.editor.mvc.view.stage.SandboxMediator;
import com.uwsoft.editor.mvc.view.stage.UIStage;
import com.uwsoft.editor.mvc.view.stage.UIStageMediator;
import com.uwsoft.editor.mvc.view.stage.input.InputListener;
import com.uwsoft.editor.renderer.SceneLoader;
import com.uwsoft.editor.renderer.components.ViewPortComponent;
import com.uwsoft.editor.renderer.legacy.data.CompositeItemVO;
import com.uwsoft.editor.renderer.legacy.data.CompositeVO;
import com.uwsoft.editor.renderer.legacy.data.LayerItemVO;
import com.uwsoft.editor.renderer.legacy.data.SceneVO;
import com.uwsoft.editor.renderer.systems.render.Overlap2dRenderer;
import com.uwsoft.editor.utils.runtime.ComponentRetriever;

/**
 * Sandbox is a complex hierarchy of managing classes that is supposed to be a main hub for the "sandbox" the part of editor where
 * user drops all panels, moves them around, and composes the scene. sandbox is responsible for using runtime to render the visual scene,
 * it is responsible to listen for all the events, item resizing, selecting, aligning, removing and things like that.
 *
 * @author azakhary
 */
public class Sandbox {

    private static Sandbox instance = null;

    private static final String CLASS_NAME = "com.uwsoft.editor.gdx.sandbox.Sandbox";

    public static final String ACTION_GROUP_ITEMS = CLASS_NAME + "ACTION_GROUP_ITEMS";
    public static final String ACTION_CAMERA_CHANGE_COMPOSITE = CLASS_NAME + "ACTION_CAMERA_CHANGE_COMPOSITE";
    public static final String ACTION_CONVERT_TO_BUTTON = CLASS_NAME + "ACTION_CONVERT_TO_BUTTON";
    public static final String ACTION_CUT = CLASS_NAME + "ACTION_CUT";
    public static final String ACTION_COPY = CLASS_NAME + "ACTION_COPY";
    public static final String ACTION_PASTE = CLASS_NAME + "ACTION_PASTE";
    public static final String ACTION_DELETE = CLASS_NAME + "ACTION_DELETE";
    public static final String ACTION_CREATE_ITEM = CLASS_NAME + "ACTION_CREATE_ITEM";
    public static final String SHOW_ADD_LIBRARY_DIALOG = CLASS_NAME + "SHOW_ADD_LIBRARY_DIALOG";
    public static final String ACTION_ADD_TO_LIBRARY = CLASS_NAME + "ACTION_ADD_TO_LIBRARY";
    public static final String ACTION_EDIT_PHYSICS = CLASS_NAME + "ACTION_EDIT_PHYSICS";
    public static final String ACTION_SET_GRID_SIZE_FROM_ITEM = CLASS_NAME + "ACTION_SET_GRID_SIZE_FROM_ITEM";
    public static final String ACTION_UPDATE_ITEM_DATA = CLASS_NAME + "ACTION_UPDATE_ITEM_DATA";
    public static final String ACTION_UPDATE_LABEL_DATA = CLASS_NAME + "ACTION_UPDATE_ILABEL_DATA";
    public static final String ACTION_ITEMS_MOVE_TO = CLASS_NAME + "ACTION_ITEMS_MOVE_TO";

    public static final String ACTION_SET_SELECTION = CLASS_NAME + "ACTION_SET_SELECTION";
    public static final String ACTION_ADD_SELECTION = CLASS_NAME + "ACTION_ADD_SELECTION";
    public static final String ACTION_RELEASE_SELECTION = CLASS_NAME + "ACTION_RELEASE_SELECTION";

    public SceneControlMediator sceneControl;
    public ItemControlMediator itemControl;

    private Object clipboard;


    private Entity currentViewingEntity;

    /**
     * this part contains legacy params that need to be removed one by one
     */
    public int currTransformType = -1;
    public Entity currTransformHost;
    public boolean isResizing = false;
    public boolean dirty = false;
    public Vector3 copedItemCameraOffset;

    public String currentLoadedSceneFileName;
    private int gridSize = 1; // pixels
    private float zoomPercent = 100;
    private UIStage uiStage;
    private ItemFactoryOld itemFactory;
    private ItemSelector selector;
    private Overlap2DFacade facade;

    private ProjectManager projectManager;
    private ResourceManager resourceManager;
    
    
    public PixelRect selectionRec;
    //public Group mainBox = new Group();
    //public SandboxUI ui;
    //public Group frontUI;

	private Engine engine;
	private Array<InputListener> listeners = new Array<InputListener>(1);


    /**
     * end of shitty part
     */


    private Sandbox() {
    }

    public synchronized static Sandbox getInstance() {
        /*
         * The instance gets created only when it is called for first time.
		 * Lazy-loading
		 */
        if (instance == null) {
            instance = new Sandbox();
            instance.init();
        }

        return instance;
    }

    private void init() {
    	facade = Overlap2DFacade.getInstance();
    	 projectManager = facade.retrieveProxy(ProjectManager.NAME);
         resourceManager = facade.retrieveProxy(ResourceManager.NAME);
    	
        
        Overlap2D overlap2D = facade.retrieveProxy(Overlap2D.NAME);
        SandboxMediator sandboxStageMediator = facade.retrieveMediator(SandboxMediator.NAME);
        UIStageMediator uiStageMediator = facade.retrieveMediator(UIStageMediator.NAME);
        uiStage = uiStageMediator.getViewComponent();
        //setUIStage(uiStage);

        engine = new Engine();
        //TODO rendering system must be added in scene loader or idk where
        Overlap2dRenderer renderer = new Overlap2dRenderer(new SpriteBatch());
        engine.addSystem(renderer);
		SceneLoader sceneLoader = new SceneLoader(engine);
		sceneLoader.setResourceManager(resourceManager);
        sceneControl = new SceneControlMediator(sceneLoader);
        itemControl = new ItemControlMediator(sceneControl);

        selector = new ItemSelector(this);
        itemFactory = new ItemFactoryOld(this);

       
    }
    
    public void initView() {
        //TODO fix and uncomment
//        getCamera().position.set(0, 0, 0);

        selectionRec = new PixelRect(0, 0);
        selectionRec.setFillColor(new Color(1, 1, 1, 0.1f));
        selectionRec.setOpacity(0.0f);
        selectionRec.setTouchable(Touchable.disabled);
        uiStage.sandBoxUIGroup.addActor(selectionRec);

    }
    
    public void setCursor(int cursor) {
        //UIController.instance.sendNotification(NameConstants.SET_CURSOR, cursor);
    }

    public void setKeyboardFocus() {
        uiStage.setKeyboardFocus(uiStage.sandBoxUIGroup);
    }
    

    /**
     * Getters *
     */

    public UIStage getUIStage() {
        return uiStage;
    }

    public ItemFactoryOld getItemFactory() {
        return itemFactory;
    }

    public SceneControlMediator getSceneControl() {
        return sceneControl;
    }
    
    public Engine getEngine() {
        return engine;
    }


    /**
     * TODO: loading fonts this way is a bit outdated and needs to change
     *
     * @param sceneName
     */
    public void initData(String sceneName) {
        SceneDataManager sceneDataManager = facade.retrieveProxy(SceneDataManager.NAME);
        ResolutionManager resolutionManager = facade.retrieveProxy(ResolutionManager.NAME);
      //TODO fix and uncomment
        //sceneDataManager.loadScene(sceneControl.getEssentials().rm.getSceneVO(sceneName), resolutionManager.currentResolutionName);

        sceneControl.initScene(sceneName);
    }

    public void loadCurrentProject(String name) {
    	//TODO fix and uncomment
        //sceneControl.getEssentials().rm = resourceManager;
        loadScene(name);
    }

    public void loadCurrentProject() {
        ProjectVO projectVO = projectManager.getCurrentProjectVO();
        loadCurrentProject(projectVO.lastOpenScene.isEmpty() ? "MainScene" : projectVO.lastOpenScene);
    }

    public void loadScene(String sceneName) {
        currentLoadedSceneFileName = sceneName;
//        uiStage.getCompositePanel().clearScenes();
        initData(sceneName);

        initView();
//        uiStage.getCompositePanel().addScene(sceneControl.getRootSceneVO());
        initSceneView(sceneControl.getRootSceneVO());
//        sandboxInputAdapter.initSandboxEvents();

        ProjectVO projectVO = projectManager.getCurrentProjectVO();
        projectVO.lastOpenScene = sceneName;
        projectManager.saveCurrentProject();
      //TODO fix and uncomment
        //sandboxStage.getCamera().position.set(0, 0, 0);

        facade.sendNotification(Overlap2D.LIBRARY_LIST_UPDATED);

        currentViewingEntity = getRootEntity();

        //TODO: move this into SceneDataManager!
        SceneDataManager sceneDataManager = facade.retrieveProxy(SceneDataManager.NAME);
        sceneDataManager.sendNotification(SceneDataManager.SCENE_LOADED);
    }

    public void initSceneView(CompositeItemVO compositeItemVO) {
    	//TODO fix and uncomment
        //initSceneView(sceneControl.initSceneView(compositeItemVO));
    }

    public void initSceneView(Entity composite) {
    	//TODO fix and uncomment
//        selector.clearSelections();
//        sandboxStage.mainBox.clear();
//        sceneControl.initSceneView(composite, true/*uiStage.getCompositePanel().isRootScene()*/);
////        if (uiStage.getCompositePanel().isRootScene()) {
////            uiStage.getCompositePanel().updateRootScene(sceneControl.getRootSceneVO());
////        }
//
//        sandboxStage.mainBox.addActor(sceneControl.getCurrentScene());
//        sceneControl.getCurrentScene().setX(0);
//        sceneControl.getCurrentScene().setY(0);
//
//        //uiStage.getLayerPanel().initContent();
//        forceContinuousParticles(composite);
    }

    /**
     * Some particle panels might not be continuous, so they will stop after first iteration, which is ok
     * This method will make sure they look continuous while in editor, so user will find and see them easily.
     *
     * @param composite composite on screen with particles to be forced to be continuous
     */
  //TODO fix and uncomment
//    private void forceContinuousParticles(CompositeItem composite) {
//        ArrayList<IBaseItem> asd = composite.getItems();
//        for (int i = 0; i < asd.size(); i++) {
//            IBaseItem item = asd.get(i);
//            if (item instanceof ParticleItem) {
//                ((ParticleItem) item).forceContinuous();
//                continue;
//            }
//            if (item instanceof CompositeItem) {
//                forceContinuousParticles((CompositeItem) item);
//            }
//
//        }
//    }

    /**
     * Well... that's a bummer, I cannot remember why this was for. but the name speaks for itself sort of.
     * TODO: figure this out
     *
     * @return SceneVO
     */
    public SceneVO sceneVoFromItems() {
        sceneControl.getCurrentSceneVO().composite = new CompositeVO();
        sceneControl.getCurrentSceneVO().composite.loadFromEntity(getRootEntity());

        return sceneControl.getCurrentSceneVO();
    }

    /**
     * Initializes current scene on screen from a tools object.
     *
     * @param vo CompositeItemVO tools
     */
    public void reconstructFromSceneVo(CompositeItemVO vo) {
        initSceneView(vo);
    }

    /**
     * TODO: what does this do? seems to be saving as checkpoint of Flow? it so it should be renamed
     */
    public void saveSceneCurrentSceneData() {
		//TODO fix and uncomment
        //sceneControl.getCurrentScene().updateDataVO();
    }

    public void setSceneAmbientColor(Color color, boolean showChange) {
        sceneControl.getCurrentSceneVO().ambientColor[0] = color.r;
        sceneControl.getCurrentSceneVO().ambientColor[1] = color.g;
        sceneControl.getCurrentSceneVO().ambientColor[2] = color.b;
        sceneControl.getCurrentSceneVO().ambientColor[3] = color.a;
      //TODO fix and uncomment
        //if (showChange) sceneControl.getEssentials().rayHandler.setAmbientLight(color);
    }

    public ItemSelector getSelector() {
        return selector;
    }

    /**
     * @deprecated
     * @return
     */
    public boolean isComponentSkinAvailable() {
        return true;
    }

    public LayerItemVO getSelectedLayer() {
        return uiStage.getCurrentSelectedLayer();
    }

    public void setCurrentlyTransforming(Entity item, int transformType) {
        if (item == null || item.getClass().getSimpleName().equals("LabelItem")) return;
        currTransformType = transformType;
        currTransformHost = item;
    }

    public Entity getCurrentScene() {
        return sceneControl.getCurrentScene();
    }

    public void enablePan() {
        //cameraPanOn = true;
        //selector.clearSelections();
        //isItemTouched = false;
    }

    public void prepareSelectionRectangle(float x, float y, boolean setOpacity) {
        // space is panning, so if we are not, then prepare the selection rectangle
        if (setOpacity) {
            selectionRec.setOpacity(0.6f);
        }
        selectionRec.setWidth(0);
        selectionRec.setHeight(0);
        selectionRec.setX(x);
        selectionRec.setY(y);
    }


    public int getZoomPercent() {
        return (int)zoomPercent;
    }

    public void setZoomPercent(float percent) {
    	//TODO fix and uncomment
//        zoomPercent = percent;
//        OrthographicCamera camera = (OrthographicCamera) (sandboxStage.getCamera());
//        camera.zoom = 1f / (zoomPercent / 100f);
    }

    public void zoomBy(float amount) {
        zoomPercent += -amount * 15f;

        if (zoomPercent < 20) zoomPercent = 20;
        if (zoomPercent > 1000) zoomPercent = 1000;

        setZoomPercent(zoomPercent);
        facade.sendNotification(Overlap2D.ZOOM_CHANGED);
    }

    public void zoomDevideBy(float amount) {

        zoomPercent /= amount;
        if (zoomPercent < 20) zoomPercent = 20;
        if (zoomPercent > 1000) zoomPercent = 1000;

        setZoomPercent(zoomPercent);
        facade.sendNotification(Overlap2D.ZOOM_CHANGED);
    }

    public int getGridSize() {
        return gridSize;
    }

    public void setGridSize(int gridSize) {
        this.gridSize = gridSize;
        facade.sendNotification(Overlap2D.GRID_SIZE_CHANGED, gridSize);
    }

    
    public Entity getRootEntity(){
    	return sceneControl.getRootEntity();
    }
    
    
    //Global Listeners part
    
    public void addListener(InputListener listener){
		if (!listeners.contains(listener, true)) {
			listeners.add(listener);
		}
		
	}
	
	public void removeListener(InputListener listener){
		listeners.removeValue(listener, true);
	}
	
	public void removeAllListener(){
		listeners.clear();
	}
	
	public Array<InputListener> getAllListeners(){
		listeners.shrink();
		return listeners;
	}

    public OrthographicCamera getCamera() {
        return (OrthographicCamera) getViewport().getCamera();
    }

    public Entity getCurrentViewingEntity() {
        return currentViewingEntity;
    }

    public void setCurrentViewingEntity(Entity entity) {
        currentViewingEntity = entity;
    }

    public ViewPortComponent getViewportComponent() {
        ViewPortComponent viewPortComponent = ComponentRetriever.get(getCurrentViewingEntity(), ViewPortComponent.class);
        return viewPortComponent;
    }

    public Viewport getViewport() {
        ViewPortComponent viewPortComponent = getViewportComponent();
        return viewPortComponent.viewPort;
    }

    public Vector2 stageToScreenCoordinates(float x, float y) {
        OrthographicCamera camera = Sandbox.getInstance().getCamera();
        Viewport viewport = Sandbox.getInstance().getViewport();
        x = x + (viewport.getScreenWidth()/2 - camera.position.x);
        y = y + (viewport.getScreenHeight()/2 - camera.position.y);

        return new Vector2(x, y);
    }

    public Vector2 screenToStageCoordinates(float x, float y) {
        OrthographicCamera camera = Sandbox.getInstance().getCamera();
        Viewport viewport = Sandbox.getInstance().getViewport();
        x = x - (viewport.getScreenWidth()/2 - camera.position.x);
        y = y - (viewport.getScreenHeight()/2 - camera.position.y);

        return new Vector2(x, y);
    }

    public void copyToClipboard(Object data) {
        //TODO: make this an actual clipboard call (dunno how though, need to make all serializable?)
        this.clipboard = data;
    }

    public Object retrieveFromClipboard() {
        //TODO: make this an actual clipboard call (dunno how though, need to make all serializable?)
        return clipboard;
    }
}
