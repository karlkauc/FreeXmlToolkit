package org.fxt.freexmltoolkit.service.telemetry;

import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Persistent telemetry settings as seen by {@link TelemetryServiceImpl}. Decouples the
 * service from {@link PropertiesService} so tests can use an in-memory implementation
 * and never touch the user's real {@code FreeXmlToolkit.properties}.
 */
public interface TelemetrySettings {

    boolean isUsageEnabled();

    void setUsageEnabled(boolean enabled);

    boolean isErrorsEnabled();

    void setErrorsEnabled(boolean enabled);

    boolean isNoticeShown();

    void setNoticeShown(boolean shown);

    /** @return the persisted install id or null */
    String getInstallId();

    void setInstallId(String installId);

    /** @return the endpoint override from the settings, or null */
    String getEndpoint();

    /** Adapter over the application's {@link PropertiesService}. */
    static TelemetrySettings of(PropertiesService props) {
        return new TelemetrySettings() {
            @Override
            public boolean isUsageEnabled() {
                return props.isTelemetryUsageEnabled();
            }

            @Override
            public void setUsageEnabled(boolean enabled) {
                props.setTelemetryUsageEnabled(enabled);
            }

            @Override
            public boolean isErrorsEnabled() {
                return props.isTelemetryErrorsEnabled();
            }

            @Override
            public void setErrorsEnabled(boolean enabled) {
                props.setTelemetryErrorsEnabled(enabled);
            }

            @Override
            public boolean isNoticeShown() {
                return props.isTelemetryNoticeShown();
            }

            @Override
            public void setNoticeShown(boolean shown) {
                props.setTelemetryNoticeShown(shown);
            }

            @Override
            public String getInstallId() {
                return props.getTelemetryInstallId();
            }

            @Override
            public void setInstallId(String installId) {
                props.setTelemetryInstallId(installId);
            }

            @Override
            public String getEndpoint() {
                return props.getTelemetryEndpoint();
            }
        };
    }

    /** Simple in-memory settings (tests, previews). */
    final class InMemory implements TelemetrySettings {
        private volatile boolean usage = true;
        private volatile boolean errors = true;
        private volatile boolean noticeShown;
        private volatile String installId;
        private volatile String endpoint;

        @Override
        public boolean isUsageEnabled() {
            return usage;
        }

        @Override
        public void setUsageEnabled(boolean enabled) {
            usage = enabled;
        }

        @Override
        public boolean isErrorsEnabled() {
            return errors;
        }

        @Override
        public void setErrorsEnabled(boolean enabled) {
            errors = enabled;
        }

        @Override
        public boolean isNoticeShown() {
            return noticeShown;
        }

        @Override
        public void setNoticeShown(boolean shown) {
            noticeShown = shown;
        }

        @Override
        public String getInstallId() {
            return installId;
        }

        @Override
        public void setInstallId(String id) {
            installId = id;
        }

        @Override
        public String getEndpoint() {
            return endpoint;
        }

        /** @param endpoint the endpoint override */
        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }
}
