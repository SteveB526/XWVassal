package mic;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.*;

import VASSAL.build.GameModule;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.widget.PieceSlot;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.Drawable;
import VASSAL.command.ChangeTracker;
import VASSAL.command.Command;
import VASSAL.command.MoveTracker;
import VASSAL.configure.HotKeyConfigurer;
import VASSAL.counters.Decorator;
import VASSAL.counters.EditablePiece;
import VASSAL.counters.FreeRotator;
import VASSAL.counters.GamePiece;
import VASSAL.counters.KeyCommand;
import VASSAL.counters.NonRectangular;
import mic.manuvers.ManeuverPaths;
import mic.manuvers.PathPart;

import static mic.Util.logToChat;


/**
 * Created by amatheny on 2/14/17.
 *
 * Second role: to completely intercept every maneuver shortcut and deal with movement AND autobump AND out of bound detection
 */
public class AutoBumpDecorator extends Decorator implements EditablePiece {
    public static final String ID = "auto-bump;";
    static final double SMALLSHAPEFUDGE = 1.01d;
    static final double LARGESHAPEFUDGE = 1.025d;

    // Set to true to enable visualizations of collision objects.
    // They will be drawn after a collision resolution, select the colliding
    // ship and press x to remove it.
    private static boolean DRAW_COLLISIONS = true;

    private final FreeRotator testRotator;

    private ShipPositionState prevPosition = null;
    private ManeuverPaths lastManeuver = null;
    private FreeRotator myRotator = null;
    public CollisionVisualization previousCollisionVisualization = null;

    private static Map<String, ManeuverPaths> keyStrokeToManeuver = ImmutableMap.<String, ManeuverPaths>builder()
            .put("SHIFT 1", ManeuverPaths.Str1)
            .put("SHIFT 2", ManeuverPaths.Str2)
            .put("SHIFT 3", ManeuverPaths.Str3)
            .put("SHIFT 4", ManeuverPaths.Str4)
            .put("SHIFT 5", ManeuverPaths.Str5)
            .put("CTRL SHIFT 1", ManeuverPaths.LT1)
            .put("CTRL SHIFT 2", ManeuverPaths.LT2)
            .put("CTRL SHIFT 3", ManeuverPaths.LT3)
            .put("ALT SHIFT 1", ManeuverPaths.RT1)
            .put("ALT SHIFT 2", ManeuverPaths.RT2)
            .put("ALT SHIFT 3", ManeuverPaths.RT3)
            .put("CTRL 1", ManeuverPaths.LBk1)
            .put("CTRL 2", ManeuverPaths.LBk2)
            .put("CTRL 3", ManeuverPaths.LBk3)
            .put("ALT 1", ManeuverPaths.RBk1)
            .put("ALT 2", ManeuverPaths.RBk2)
            .put("ALT 3", ManeuverPaths.RBk3)
            .put("ALT CTRL 1", ManeuverPaths.K1)
            .put("ALT CTRL 2", ManeuverPaths.K2)
            .put("ALT CTRL 3", ManeuverPaths.K3)
            .put("ALT CTRL 4", ManeuverPaths.K4)
            .put("ALT CTRL 5", ManeuverPaths.K5)
            .put("CTRL 6", ManeuverPaths.RevLbk1)
            .put("SHIFT 6", ManeuverPaths.RevStr1)
            .put("ALT 6", ManeuverPaths.RevRbk1)
            .put("CTRL Q", ManeuverPaths.SloopL1)
            .put("CTRL W", ManeuverPaths.SloopL2)
            .put("CTRL E", ManeuverPaths.SloopL3)
            .put("ALT Q", ManeuverPaths.SloopR1)
            .put("ALT W", ManeuverPaths.SloopR2)
            .put("ALT E", ManeuverPaths.SloopR3)
            .put("CTRL SHIFT E", ManeuverPaths.SloopL3Turn)
            .put("ALT SHIFT E", ManeuverPaths.SloopR3Turn)
            .put("CTRL Y", ManeuverPaths.TrollL2)
            .put("CTRL T", ManeuverPaths.TrollL3)
            .put("ALT Y", ManeuverPaths.TrollR2)
            .put("ALT T", ManeuverPaths.TrollR3)
            .build();

    public AutoBumpDecorator() {
        this(null);
    }

    public AutoBumpDecorator(GamePiece piece) {
        setInner(piece);
        this.testRotator = new FreeRotator("rotate;360;;;;;;;", null);
    }

    @Override
    public void mySetState(String s) {

    }

    @Override
    public String myGetState() {
        return "";
    }

    @Override
    protected KeyCommand[] myGetKeyCommands() {
        return new KeyCommand[0];
    }

    @Override
    public Command myKeyEvent(KeyStroke keyStroke) {
        return null;
    }

    private boolean isAutobumpTrigger(KeyStroke stroke) {
        return KeyStroke.getAWTKeyStroke(KeyEvent.VK_C, 0, false).equals(stroke);
    }

    @Override
    public Command keyEvent(KeyStroke stroke) {

        //Any keystroke made on a ship will remove the orange shades
        if (this.previousCollisionVisualization != null) {
            logToChat("should delete previous orange shapes here.");
            getMap().removeDrawComponent(this.previousCollisionVisualization);
        }

        //Start a new batch of potential CollisionVisualization, give it an empty rectangle so it
        //at least as a non-null value
        CollisionVisualization cvSaveList = new CollisionVisualization(new Rectangle(0,0,0,0));

        ManeuverPaths path = getKeystrokePath(stroke);
        // Is this a keystroke for a maneuver? Deal with the 'no' cases first
        if (path == null) {
            //check to see if 'c' was pressed
            if(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0, false).equals(stroke))
            {
                List<BumpableWithShape> otherShipShapes = getShipsWithShapes();

//TO DO place an extended template here

                /*
                String collisionAideName = "";
                if(ManeuverPaths.LBk1.equals(path))
                    collisionAideName = "Prolonged Bank 1";
PieceSlot ps = new PieceSlot();
                GamePiece piece = Util.newPiece(this.ship);

                spawnPiece(GamePiece piece, new Point(), getMap());
*/
                boolean isCollisionOccuring = findCollidingEntity(getBumpableCompareShape(this), otherShipShapes) != null ? true : false;
                //backtracking requested with a detected bumpable overlap, deal with it
                if(isCollisionOccuring)
                {
                    Command innerCommand = piece.keyEvent(stroke);
                    Command bumpResolveCommand = resolveBump(otherShipShapes);
                    return bumpResolveCommand == null ? innerCommand : innerCommand.append(bumpResolveCommand);
                }
            }
            // 'c' keystroke has finished here, leave the method altogether
            return piece.keyEvent(stroke);
        }

        // We know we're dealing with a maneuver keystroke
// TO DO include decloaks, barrel rolls
        if (stroke.isOnKeyRelease() == false) {

            // find the list of other bumpables
            List<BumpableWithShape> otherBumpableShapes = getBumpablesWithShapes();

            //safeguard old position and path

            this.prevPosition = getCurrentState();
            this.lastManeuver = path;

            //This PathPart list will be used everywhere: moving, bumping, out of boundsing
            //maybe fetch it for both 'c' behavior and movement
            final List<PathPart> parts = path.getTransformedPathParts(
                    this.getCurrentState().x,
                    this.getCurrentState().y,
                    this.getCurrentState().angle,
                    isLargeShip(this)
            );

            //this is the final ship position post-move
            PathPart part = parts.get(parts.size()-1);

            //Get the ship name string for announcements
            String yourShipName = getShipStringForReports(true);

            //Start the Command chain
            Command innerCommand = piece.keyEvent(stroke);

            innerCommand.append(buildTranslateCommand(part, path.getAdditionalAngleForShip()));
            logToChat("* --- " + yourShipName + " performs move: " + path.getFullName());

//DONE: find move's real template shape
//TO DO: check for collisions with obstacles and mines
//TO DO: if a collision is found, display the template


            //These lines fetch the Shape of the last movement template used
            FreeRotator rotator = (FreeRotator) (Decorator.getDecorator(Decorator.getOutermost(this), FreeRotator.class));
            Shape lastMoveShapeUsed = path.getTransformedTemplateShape(this.getPosition().getX(),
                    this.getPosition().getY(),
                    isLargeShip(this),
                    rotator);
//Check for template shape overlap with mines, asteroids, debris
            checkTemplateOverlap(lastMoveShapeUsed, otherBumpableShapes, cvSaveList);
//Check for ship bumping other ships, mines, asteroids, debris
            announceBumpAndPaint(otherBumpableShapes, cvSaveList);
//Check if a ship becomes out of bounds
            checkIfOutOfBounds(yourShipName, cvSaveList);

            //keep track of a backup of all cv's for termination next keystroke.
            this.previousCollisionVisualization = cvSaveList;
        }



        //the maneuver has finished. return control of the event to vassal to do nothing
        return piece.keyEvent(stroke);
    }

    private void checkTemplateOverlap(Shape lastMoveShapeUsed, List<BumpableWithShape> otherBumpableShapes,
                                      CollisionVisualization cvSaveList) {

        List<BumpableWithShape> collidingEntities = findCollidingEntities(lastMoveShapeUsed, otherBumpableShapes);
        CollisionVisualization cvFoundHere = new CollisionVisualization(lastMoveShapeUsed);
        for (BumpableWithShape bumpedBumpable : collidingEntities) {
            if (DRAW_COLLISIONS) {
                String yourShipName = getShipStringForReports(true);
                if (bumpedBumpable.type.equals("Asteroid")) {
                    String bumpAlertString = "* --- Overlap detected with " + yourShipName + "'s maneuver template and an asteroid.";
                    logToChat(bumpAlertString);
                } else if (bumpedBumpable.type.equals("Debris")) {
                    String bumpAlertString = "* --- Overlap detected with " + yourShipName + "'s maneuver template and a debris cloud.";
                    logToChat(bumpAlertString);
                } else if (bumpedBumpable.type.equals("Bomb")) {
                    String bumpAlertString = "* --- Overlap detected with " + yourShipName + "'s maneuver template and a bomb.";
                    logToChat(bumpAlertString);
                }
                cvFoundHere.add(bumpedBumpable.shape);
                cvSaveList.add(bumpedBumpable.shape);
            }
        }
        if (collidingEntities.size() > 0) {
            cvFoundHere.add(lastMoveShapeUsed);
            cvSaveList.add(lastMoveShapeUsed);

            getMap().addDrawComponent(cvFoundHere);
        }
    }


    private void spawnPiece(GamePiece piece, Point position, VASSAL.build.module.Map map) {
        Command placeCommand = map.placeOrMerge(piece, position);
        placeCommand.execute();
        GameModule.getGameModule().sendAndLog(placeCommand);
    }


    private void checkIfOutOfBounds(String yourShipName, CollisionVisualization cvSaveList) {
        Rectangle mapArea = new Rectangle(0,0,0,0);
        try{
            Board b = getMap().getBoards().iterator().next();
            mapArea = b.bounds();
            String name = b.getName();
        }catch(Exception e)
        {
            logToChat("Board name isn't formatted right, change to #'x#' Description");
        }
        Shape theShape = getBumpableCompareShape(this);

        if(theShape.getBounds().getMaxX() > mapArea.getBounds().getMaxX()  || // too far to the right
                theShape.getBounds().getMaxY() > mapArea.getBounds().getMaxY() || // too far to the bottom
                theShape.getBounds().getX() < mapArea.getBounds().getX() || //too far to the left
                theShape.getBounds().getY() < mapArea.getBounds().getY()) // too far to the top
        {

            logToChat("* -- " + yourShipName + " flew out of bounds");
            CollisionVisualization collisionVisualization = new CollisionVisualization(theShape);
            cvSaveList.add(theShape);

            getMap().addDrawComponent(collisionVisualization);
        }
    }

    private void announceBumpAndPaint(List<BumpableWithShape> otherBumpableShapes, CollisionVisualization cvSaveList) {
        Shape theShape = getBumpableCompareShape(this);

        List<BumpableWithShape> collidingEntities = findCollidingEntities(theShape, otherBumpableShapes);
        CollisionVisualization cvFoundHere = new CollisionVisualization(theShape);
        for (BumpableWithShape bumpedBumpable : collidingEntities) {
            if (DRAW_COLLISIONS) {
                String yourShipName = getShipStringForReports(true);
                if (bumpedBumpable.type.equals("Ship")) {
                    String otherShipName = getShipStringForReports(false);
                    String bumpAlertString = "* --- Overlap detected with " + yourShipName + " and " + otherShipName + ". Resolve this by hitting the 'c' key.";
                    logToChat(bumpAlertString);
                } else if (bumpedBumpable.type.equals("Asteroid")) {
                    String bumpAlertString = "* --- Overlap detected with " + yourShipName + " and an asteroid.";
                    logToChat(bumpAlertString);
                } else if (bumpedBumpable.type.equals("Debris")) {
                    String bumpAlertString = "* --- Overlap detected with " + yourShipName + " and a debris cloud.";
                    logToChat(bumpAlertString);
                } else if (bumpedBumpable.type.equals("Bomb")) {
                    String bumpAlertString = "* --- Overlap detected with " + yourShipName + " and a bomb.";
                    logToChat(bumpAlertString);
                }
                cvFoundHere.add(bumpedBumpable.shape);
                cvSaveList.add(bumpedBumpable.shape);
            }
        }
        if (collidingEntities.size() > 0) getMap().addDrawComponent(cvFoundHere);

    }

    private String getShipStringForReports(boolean isYours)
    {
        String yourShipName = (isYours ? "your ship" : "another ship");
        if (this.getProperty("Pilot Name").toString().length() > 0) { yourShipName += " " + this.getProperty("Pilot Name").toString(); }
        if (this.getProperty("Craft ID #").toString().length() > 0) { yourShipName += " (" + this.getProperty("Craft ID #").toString() + ")"; }

        return yourShipName;
    }
    /**
     * Iterate in reverse over path of last maneuver and return a command that
     * will move the ship to a non-overlapping position and rotation
     *
     * @return
     */
    private Command resolveBump(List<BumpableWithShape> otherBumpableShapes) {
        if (this.lastManeuver == null || this.prevPosition == null) {
            return null;
        }
        Shape rawShape = getRawShape(this);
        final List<PathPart> parts = this.lastManeuver.getTransformedPathParts(
                this.prevPosition.x,
                this.prevPosition.y,
                this.prevPosition.angle,
                isLargeShip(this)
        );

        for (int i = parts.size() - 1; i >= 0; i--) {
            PathPart part = parts.get(i);
            Shape movedShape = AffineTransform
                    .getTranslateInstance(part.getX(), part.getY())
                    .createTransformedShape(rawShape);
            double roundedAngle = convertAngleToGameLimits(part.getAngle());
            movedShape = AffineTransform
                    .getRotateInstance(Math.toRadians(-roundedAngle), part.getX(), part.getY())
                    .createTransformedShape(movedShape);

            BumpableWithShape bumpedBumpable = findCollidingEntity(movedShape, otherBumpableShapes);
            if (bumpedBumpable == null) {
                if (DRAW_COLLISIONS) {
                    if (this.previousCollisionVisualization != null) {
                        getMap().removeDrawComponent(this.previousCollisionVisualization);
                    }
                }
                return buildTranslateCommand(part,0.0f);
            }
        }

        // Could not find a position that wasn't bumping, bring it back to where it was before
        return buildTranslateCommand(new PathPart(this.prevPosition.x, this.prevPosition.y, this.prevPosition.angle), 0.0f);
    }

    /**
     * Builds vassal command to transform the current ship to the given PathPart
     *
     * @param part
     * @return
     */
    private Command buildTranslateCommand(PathPart part, double additionalAngle) {
        // Copypasta from VASSAL.counters.Pivot
        ChangeTracker changeTracker = new ChangeTracker(this);
        getRotator().setAngle(part.getAngle() + additionalAngle);
        setProperty("Moved", Boolean.TRUE);
        Command result = changeTracker.getChangeCommand();

        GamePiece outermost = Decorator.getOutermost(this);
        MoveTracker moveTracker = new MoveTracker(outermost);
        Point point = new Point((int) Math.floor(part.getX() + 0.5), (int) Math.floor(part.getY() + 0.5));
        // ^^ There be dragons here ^^ - vassals gives positions as doubles but only lets them be set as ints :(
        this.getMap().placeOrMerge(outermost, point);
        result = result.append(moveTracker.getMoveCommand());

        /*MovementReporter reporter = new MovementReporter(result);

        Command reportCommand = reporter.getReportCommand();
        if (reportCommand != null) {
            reportCommand.execute();
            result = result.append(reportCommand);
        }

        result = result.append(reporter.markMovedPieces());
*/
        return result;
    }

    /**
     * Returns the comparision shape of the first bumpable colliding with the provided ship.  Returns null if there
     * are no collisions
     *
     * @param myTestShape
     * @return
     */
    private BumpableWithShape findCollidingEntity(Shape myTestShape, List<BumpableWithShape> otherShapes) {
        List<BumpableWithShape> allCollidingEntities = findCollidingEntities(myTestShape, otherShapes);
        if (allCollidingEntities.size() > 0) {
            return allCollidingEntities.get(0);
        } else {
            return null;
        }
    }

    /**
     * Returns a list of all bumpables colliding with the provided ship.  Returns an empty list if there
     * are no collisions
     *
     * @param myTestShape
     * @return
     */
    private List<BumpableWithShape> findCollidingEntities(Shape myTestShape, List<BumpableWithShape> otherShapes) {
        List<BumpableWithShape> shapes = Lists.newLinkedList();
        for (BumpableWithShape otherBumpableShape : otherShapes) {
            if (shapesOverlap(myTestShape, otherBumpableShape.shape)) {
                shapes.add(otherBumpableShape);
            }
        }
        return shapes;
    }

    /**
     * Returns true if the two provided shapes areas have any intersection
     *
     * @param shape1
     * @param shape2
     * @return
     */
    private boolean shapesOverlap(Shape shape1, Shape shape2) {
        Area a1 = new Area(shape1);
        a1.intersect(new Area(shape2));
        return !a1.isEmpty();
    }

    public void draw(Graphics graphics, int i, int i1, Component component, double v) {
        this.piece.draw(graphics, i, i1, component, v);
    }

    public Rectangle boundingBox() {
        return this.piece.boundingBox();
    }

    public Shape getShape() {
        return this.piece.getShape();
    }

    public String getName() {
        return this.piece.getName();
    }

    @Override
    public String myGetType() {
        return ID;
    }

    public String getDescription() {
        return "Custom auto-bump resolution (mic.AutoBumpDecorator)";
    }

    public void mySetType(String s) {

    }

    public HelpFile getHelpFile() {
        return null;
    }

    /**
     * Returns FreeRotator decorator associated with this instance
     *
     * @return
     */
    private FreeRotator getRotator() {
        if (this.myRotator == null) {
            this.myRotator = ((FreeRotator) Decorator.getDecorator(getOutermost(this), FreeRotator.class));
        }
        return this.myRotator;
    }

    /**
     * Returns a new ShipPositionState based on the current position and angle of this ship
     *
     * @return
     */
    private ShipPositionState getCurrentState() {
        ShipPositionState shipState = new ShipPositionState();
        shipState.x = getPosition().getX();
        shipState.y = getPosition().getY();
        shipState.angle = getRotator().getAngle();
        return shipState;
    }

    /**
     * Finds any maneuver paths related to the keystroke based on the map
     * keyStrokeToManeuver map
     *
     * @param keyStroke
     * @return
     */
    private ManeuverPaths getKeystrokePath(KeyStroke keyStroke) {
        String hotKey = HotKeyConfigurer.getString(keyStroke);
        if (keyStrokeToManeuver.containsKey(hotKey)) {
            return keyStrokeToManeuver.get(hotKey);
        }
        return null;
    }

    private List<BumpableWithShape> getShipsWithShapes() {
        List<BumpableWithShape> ships = Lists.newLinkedList();
        for (BumpableWithShape ship : getShipsOnMap()) {
            if (getId().equals(ship.bumpable.getId())) {
                continue;
            }
            ships.add(ship);
        }
        return ships;
    }

    private List<BumpableWithShape> getBumpablesWithShapes() {
        List<BumpableWithShape> bumpables = Lists.newLinkedList();
        for (BumpableWithShape bumpable : getBumpablesOnMap()) {
            if (getId().equals(bumpable.bumpable.getId())) {
                continue;
            }
            bumpables.add(bumpable);
        }
        return bumpables;
    }

    private List<BumpableWithShape> getShipsOnMap() {
        List<BumpableWithShape> ships = Lists.newArrayList();

        GamePiece[] pieces = getMap().getAllPieces();
        for (GamePiece piece : pieces) {
            if (piece.getState().contains("Ship")) {
                ships.add(new BumpableWithShape((Decorator)piece, getBumpableCompareShape((Decorator)piece), "Ship"));
            }
        }
        return ships;
    }

    private List<BumpableWithShape> getBumpablesOnMap() {
        List<BumpableWithShape> bumpables = Lists.newArrayList();

        GamePiece[] pieces = getMap().getAllPieces();
        for (GamePiece piece : pieces) {
            if (piece.getState().contains("Ship")) {
                bumpables.add(new BumpableWithShape((Decorator)piece, getBumpableCompareShape((Decorator)piece), "Ship"));
            } else if (piece.getState().contains("Asteroid")) {
                // comment out this line and the next three that add to bumpables if bumps other than with ships shouldn't be detected yet
                bumpables.add(new BumpableWithShape((Decorator)piece, getBumpableCompareShape((Decorator)piece), "Asteroid"));
            } else if (piece.getState().contains("Debris")) {
                bumpables.add(new BumpableWithShape((Decorator)piece, getBumpableCompareShape((Decorator)piece), "Debris"));
            } else if (piece.getState().contains("Bomb")) {
                bumpables.add(new BumpableWithShape((Decorator)piece, getBumpableCompareShape((Decorator)piece), "Bomb"));
            }
        }
        return bumpables;
    }

    /**
     * Uses a FreeRotator unassociated with any game pieces with a 360 rotation limit
     * to convert the provided angle to the same angle the ship would be drawn at
     * by vassal
     *
     * @param angle
     * @return
     */
    private double convertAngleToGameLimits(double angle) {
        this.testRotator.setAngle(angle);
        return this.testRotator.getAngle();
    }

    /**
     * Finds non-rectangular mask layer of provided ship.  This is the shape with only the base
     * and nubs
     *
     * @param bumpable
     * @return
     */
    private Shape getRawShape(Decorator bumpable) {
        return Decorator.getDecorator(Decorator.getOutermost(bumpable), NonRectangular.class).getShape();
    }

    /**
     * Finds raw ship mask and translates and rotates it to the current position and heading
     * of the ship
     *
     * @param bumpable
     * @return Translated ship mask
     */
    private Shape getBumpableCompareShape(Decorator bumpable) {
        Shape rawShape = getRawShape(bumpable);
        double scaleFactor = isLargeShip(bumpable) ? LARGESHAPEFUDGE : SMALLSHAPEFUDGE;
        Shape transformed = AffineTransform.getScaleInstance(scaleFactor, scaleFactor).createTransformedShape(rawShape);

        transformed = AffineTransform
                .getTranslateInstance(bumpable.getPosition().getX(), bumpable.getPosition().getY())
                .createTransformedShape(transformed);

        FreeRotator rotator = (FreeRotator) (Decorator.getDecorator(Decorator.getOutermost(bumpable), FreeRotator.class));
        double centerX = bumpable.getPosition().getX();
        double centerY = bumpable.getPosition().getY();
        transformed = AffineTransform
                .getRotateInstance(rotator.getAngleInRadians(), centerX, centerY)
                .createTransformedShape(transformed);

        return transformed;
    }


    private boolean isLargeShip(Decorator ship) {
        return getRawShape(ship).getBounds().getWidth() > 114;
    }

    private static class CollisionVisualization implements Drawable {

        private final List<Shape> shapes;

        CollisionVisualization(Shape shipShape) {
            this.shapes = new ArrayList<Shape>();
            this.shapes.add(shipShape);
        }

        public void add(Shape bumpable) {
            this.shapes.add(bumpable);
        }

        public void draw(Graphics graphics, VASSAL.build.module.Map map) {
            Graphics2D graphics2D = (Graphics2D) graphics;
            Color myO = new Color(255,99,71,150);
            graphics2D.setColor(myO);
            AffineTransform scaler = AffineTransform.getScaleInstance(map.getZoom(), map.getZoom());
            for (Shape shape : shapes) {
                graphics2D.fill(scaler.createTransformedShape(shape));
            }
        }

        public boolean drawAboveCounters() {
            return true;
        }
    }

    private static class ShipPositionState {
        double x;
        double y;
        double angle;
    }

    public class BumpableWithShape {
        Shape shape;
        Decorator bumpable;
        String type;
        BumpableWithShape(Decorator bumpable, Shape shape, String type) {
            this.bumpable = bumpable;
            this.shape = shape;
            this.type = type;
        }
    }
}