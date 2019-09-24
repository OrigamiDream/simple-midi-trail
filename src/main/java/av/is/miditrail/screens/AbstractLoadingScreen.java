package av.is.miditrail.screens;

import studio.avis.juikit.Juikit;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractLoadingScreen extends AbstractScreen {

    private final AtomicBoolean done = new AtomicBoolean();

    public AbstractLoadingScreen(Juikit juikit, ScreenManager screenManager) {
        super(juikit, screenManager);
    }

    @Override
    public void enterPage() {
        startDimming();

        Thread thread = new Thread(() -> {
            asyncLoad();
            done.set(true);
        });
        thread.setDaemon(true);
        thread.start();

        juikit.afterPainter((uikit, graphics) -> {
            if(isDone()) {
                return;
            }

            displayLoading(graphics, !done.get(), this::onLoad);
        });

        onEnterPage();
    }

    abstract void onEnterPage();

    abstract void asyncLoad();

    abstract void onLoad();
}
