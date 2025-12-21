package org.fxt.freexmltoolkit.controls.editor;

import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.service.ThreadPoolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactfx.collection.LiveList;
import org.xml.sax.SAXParseException;
import org.fxmisc.richtext.model.Paragraph; // Import Paragraph
import java.util.Collection; // Import Collection
import org.fxmisc.richtext.model.TwoDimensional.Bias;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class XmlValidationManagerTest {

    private XmlValidationManager validationManager;
    private ThreadPoolManager threadPoolManager;
    
    @Mock
    private CodeArea codeArea;
    @Mock
    private LiveList<Paragraph<Collection<String>,String,Collection<String>>> mockParagraphs; // Correct generic type

    // A synchronous executor for UI updates in tests
    private Consumer<Runnable> synchronousUiExecutor;

    @BeforeEach
    public void setUp() {
        threadPoolManager = ThreadPoolManager.getInstance();
        
        // Mock CodeArea.getParagraphs() to return a LiveList with some size
        lenient().when(mockParagraphs.size()).thenReturn(10); // Assume 10 paragraphs for testing line numbers
        lenient().when(codeArea.getParagraphs()).thenReturn(mockParagraphs);
        
        // This executor will run the UI updates directly, synchronously
        synchronousUiExecutor = Runnable::run; 
        
        validationManager = new XmlValidationManager(codeArea, threadPoolManager, synchronousUiExecutor);
    }

    @Test
    public void testPerformLiveValidationExecutesInBackground() throws InterruptedException {
        CountDownLatch validationCompleteLatch = new CountDownLatch(1);
        AtomicBoolean validationRun = new AtomicBoolean(false);

        validationManager.setValidationService(text -> {
            // Simulate slow validation
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            validationRun.set(true);
            return new ArrayList<>();
        });

        validationManager.setValidationCompleteCallback(validationCompleteLatch::countDown);

        validationManager.performLiveValidation("<root></root>");

        assertTrue(validationCompleteLatch.await(5, TimeUnit.SECONDS), "Validation should complete");
        assertTrue(validationRun.get(), "Validation service should have been called");
    }

    @Test
    public void testApplyErrorHighlightingUpdatesStateAndInvokesCallback() {
        List<SAXParseException> errors = new ArrayList<>();
        errors.add(new SAXParseException("Error on line 1", null, null, 1, 1));
        errors.add(new SAXParseException("Error on line 3", null, null, 3, 5));
        
        CountDownLatch callbackLatch = new CountDownLatch(1);
        validationManager.setErrorCallback(errMap -> {
            assertEquals(2, errMap.size(), "Error map should contain 2 errors");
            assertTrue(errMap.containsKey(1));
            assertTrue(errMap.containsKey(3));
            assertEquals("Error on line 1", errMap.get(1));
            callbackLatch.countDown();
        });

        validationManager.applyErrorHighlighting(errors);
        
        try {
            assertTrue(callbackLatch.await(1, TimeUnit.SECONDS), "Error callback should be invoked");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<Integer, String> currentErrors = validationManager.getCurrentErrors();
        assertEquals(2, currentErrors.size(), "Internal error state should have 2 errors");
        assertTrue(currentErrors.containsKey(1));
        assertTrue(currentErrors.containsKey(3));
        assertEquals("Error on line 1", currentErrors.get(1));
    }
    
    @Test
    public void testClearErrorsResetsStateAndInvokesCallback() {
        // Set up some initial errors
        validationManager.applyErrorHighlighting(Collections.singletonList(
            new SAXParseException("Initial error", null, null, 1, 1)));
        
        CountDownLatch callbackLatch = new CountDownLatch(1);
        validationManager.setErrorCallback(errMap -> {
            assertTrue(errMap.isEmpty(), "Error map should be empty after clearing");
            callbackLatch.countDown();
        });
        
        validationManager.clearErrors();
        
        try {
            assertTrue(callbackLatch.await(1, TimeUnit.SECONDS), "Error callback should be invoked after clearing");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertTrue(validationManager.getCurrentErrors().isEmpty(), "Internal error state should be empty");
        assertEquals(0, validationManager.getErrorCount(), "Error count should be 0");
    }

    @Test
    public void testPerformLiveValidationHandlesCancellation() throws InterruptedException {
        CountDownLatch validationServiceCalledLatch = new CountDownLatch(1);
        AtomicBoolean validationCompletedNormally = new AtomicBoolean(false);

        validationManager.setValidationService(text -> {
            validationServiceCalledLatch.countDown();
            try {
                Thread.sleep(500); // Simulate long running task
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Validation interrupted"); // Throw CancellationException on interrupt
            }
            validationCompletedNormally.set(true);
            return new ArrayList<>();
        });
        
        validationManager.setValidationCompleteCallback(() -> { /* Should not be called on cancellation */ });

        validationManager.performLiveValidation("<root><child/></root>");
        
        // Wait for validation service to be called
        assertTrue(validationServiceCalledLatch.await(1, TimeUnit.SECONDS), "Validation service should be called");
        
        // Cancel validation immediately after it started
        validationManager.cancelValidation();
        
        // The CompletableFuture should complete exceptionally due to cancellation
        // The whenCompleteAsync callback will run on the uiExecutor (synchronousUiExecutor)
        // so we don't need a separate latch for that.
        
        // Give some time for the CompletableFuture's whenCompleteAsync to process
        Thread.sleep(100); 

        assertFalse(validationCompletedNormally.get(), "Validation should not complete normally after cancellation");
        
        // Verify that the internal errors state is cleared if validation was cancelled
        // This is handled by the `else if (throwable != null)` block in performLiveValidation
        // and then `clearErrors()` is called.
        assertTrue(validationManager.getCurrentErrors().isEmpty(), "Errors should be cleared on cancellation/failure");
    }
}