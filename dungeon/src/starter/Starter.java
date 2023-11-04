package starter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;

import contrib.configuration.KeyboardConfig;
import contrib.crafting.Crafting;
import contrib.entities.EntityFactory;
import contrib.hud.OkDialog;
import contrib.systems.*;

import core.Entity;
import core.Game;
import core.components.PlayerComponent;
import core.level.elements.ILevel;

import dungeonFiles.DSLEntryPoint;
import dungeonFiles.DslFileLoader;
import dungeonFiles.DungeonConfig;

import graphconverter.TaskGraphConverter;

import interpreter.DSLEntryPointFinder;
import interpreter.DSLInterpreter;

import task.Task;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Generic Game starter.Starter for a game that uses DSL inputs.
 *
 * <p>This will set up a basic game with all systems and a hero.
 *
 * <p>It reads command line arguments that are paths to DSL files or jars.
 *
 * <p>Not yet implemented: Letting the player select a starting point (essentially a level) from the
 * input DSL files and loading the game.
 *
 * <p>Start with "./gradlew start --args "dungeon/assets/scripts/task_test.dng" " or with other dsl
 * paths.
 */
public class Starter {
    private static final String BACKGROUND_MUSIC = "sounds/background.wav";
    private static boolean realGameStarted = false;
    private static final DSLInterpreter dslInterpreter = new DSLInterpreter();

    private static final Consumer<Entity> showQuestLog =
            entity -> {
                StringBuilder questLogBuilder = new StringBuilder();
                Task.allTasks()
                        .filter(t -> t.state() == Task.TaskState.PROCESSING_ACTIVE)
                        .forEach(
                                task ->
                                        questLogBuilder
                                                .append(task.taskText())
                                                .append(" ID(")
                                                .append(task.id())
                                                .append(")")
                                                .append(System.lineSeparator())
                                                .append(System.lineSeparator()));
                String questLog = questLogBuilder.toString();
                OkDialog.showOkDialog(questLog, "Questlog", () -> {});
            };

    public static void main(String[] args) throws IOException {
        // read in DSL-Files
        Set<DSLEntryPoint> entryPoints = processCLIArguments(args);

        // some game Setup
        configGame();
        // will load the level to select the task/DSL-Entrypoint on Game start
        wizardSelectorOnSetup(entryPoints);

        // will generate the TaskDependencyGraph, execute the TaskBuilder, generate and set the
        // Level and generate the PetriNet after the player selected an DSLEntryPoint
        onEntryPointSelection();

        Game.run();
    }

    private static void onEntryPointSelection() {
        Game.userOnFrame(
                () -> {
                    debugKey();
                    // the player selected a Task/DSL-Entrypoint but it´s not loaded yet:
                    if (!realGameStarted && WizardTaskSelector.selectedDSLEntryPoint != null) {
                        realGameStarted = true;

                        DungeonConfig config =
                                dslInterpreter.interpretEntryPoint(
                                        WizardTaskSelector.selectedDSLEntryPoint);
                        ILevel level =
                                TaskGraphConverter.convert(
                                        config.dependencyGraph(), dslInterpreter);

                        Game.currentLevel(level);
                    }
                });
    }

    private static void wizardSelectorOnSetup(Set<DSLEntryPoint> entryPoints) {
        Game.userOnSetup(
                () -> {
                    createHero();
                    createSystems();
                    Game.currentLevel(WizardTaskSelector.wizardLevel());
                    setupMusic();
                });

        // load the wizard task selector level
        Game.userOnLevelLoad(
                (firstTime) -> {
                    // this will be at the start of the game
                    if (firstTime && WizardTaskSelector.selectedDSLEntryPoint == null) {
                        try {
                            Game.add(
                                    WizardTaskSelector.wizard(
                                            WizardTaskSelector.selectTaskQuestion(entryPoints)));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
    }

    private static Set<DSLEntryPoint> processCLIArguments(String[] args) throws IOException {
        Set<DSLEntryPoint> entryPoints = new HashSet<>();
        DSLEntryPointFinder finder = new DSLEntryPointFinder();
        DslFileLoader.processArguments(args)
                .forEach(path -> finder.getEntryPoints(path).ifPresent(entryPoints::addAll));
        return entryPoints;
    }

    private static void createHero() {
        Entity hero;
        try {
            hero = (EntityFactory.newHero());
            hero.fetch(PlayerComponent.class)
                    .flatMap(
                            fetch ->
                                    fetch.registerCallback(
                                            KeyboardConfig.QUESTLOG.value(),
                                            showQuestLog,
                                            false,
                                            true));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Game.add(hero);
        Game.hero(hero);
    }

    private static void configGame() throws IOException {
        Game.initBaseLogger();
        Game.windowTitle("DSL Dungeon");
        Game.frameRate(30);
        Game.disableAudio(false);
        Game.loadConfig(
                "dungeon_config.json",
                contrib.configuration.KeyboardConfig.class,
                core.configuration.KeyboardConfig.class);
        Crafting.loadRecipes();
    }

    private static void createSystems() {
        Game.add(new AISystem());
        Game.add(new CollisionSystem());
        Game.add(new HealthSystem());
        Game.add(new XPSystem());
        Game.add(new ProjectileSystem());
        Game.add(new HealthbarSystem());
        Game.add(new HeroUISystem());
        Game.add(new HudSystem());
        Game.add(new SpikeSystem());
        Game.add(new IdleSoundSystem());
    }

    private static void setupMusic() {
        Music backgroundMusic = Gdx.audio.newMusic(Gdx.files.internal(BACKGROUND_MUSIC));
        backgroundMusic.setLooping(true);
        backgroundMusic.play();
        backgroundMusic.setVolume(.1f);
    }

    private static void debugKey() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            Task.allTasks()
                    .filter(t -> t.state() == Task.TaskState.PROCESSING_ACTIVE)
                    .findFirst()
                    .get()
                    .state(Task.TaskState.FINISHED_CORRECT);
            System.out.println(Task.allTasks());
        }
    }
}
