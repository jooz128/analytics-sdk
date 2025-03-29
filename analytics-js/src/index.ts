enum EventType {
    invalid = "invalid",
    identify = "identify",
    track = "track"
}

type Event = {
    anonymousId: string
    userId: string | null
    messageId: string
    type: EventType
    context?: Record<string, any>
    originalTimestamp: string
    event?: string | null,
    properties?: Record<string, any>
    traits?: Record<string, any>
    writeKey: string
}

class Analytics {
    private static instance: Analytics;
    private queue: Event[] = [];
    private userId: string | null = null;
    private anonymousId: string;
    private endpoint = "http://localhost:8080/v1/batch";
    private writeKey = ""
    private batchSize = 20; // Default batch size TODO: update this to 20
    private flushInterval = 30000; // Default 30 seconds
    private flushTimer: any;
    private context: Record<string, any> = {}

    private constructor() {
        this.anonymousId = this.getAnonymousId();
        this.restoreQueue();
        this.startFlushTimer();
    }

    public static getInstance(): Analytics {
        if (typeof window === "undefined") {
            return Analytics.instance;
        }
        if (!Analytics.instance) {
            Analytics.instance = new Analytics();
        }
        return Analytics.instance;
    }

    public async init(config: {
        writeKey: string, endpoint?: string, batchSize?: number,
        flushInterval?: number, enablePageTracking?: boolean
    }) {
        this.writeKey = config.writeKey
        this.endpoint = config.endpoint || this.endpoint
        const userAgentData = (navigator as any).userAgentData
            ? await (navigator as any).userAgentData.getHighEntropyValues(["architecture", "bitness", "brands",
                "mobile", "model", "platform", "platformVersion", "uaFullVersion"]) // additional fields fullVersionList
            : null;
        this.context = {
            userAgentData: userAgentData,
            library: {
                name: "@d1414k/analytics-js",
                version: "1.0.3"
            }
        };
        if (config.batchSize) this.batchSize = config.batchSize;
        if (config.flushInterval) {
            this.flushInterval = config.flushInterval;
            this.startFlushTimer(); // if flushInterval changed we need to restart this timer 
        }

        if (config.enablePageTracking) this.setupPageTracking()
    }

    public identify(userId: string, traits: Record<string, any> = {}): void {
        if (!this.writeKey) {
            throw new Error("Analytics is not initialized. Call Analytics.init({ writeKey: 'your-key' }) before using identify.");
        }
        this.userId = userId;
        localStorage.setItem("analytics_user_id", userId);
        let event = this.getDefaultEvent()
        event.context = {} // we don't need context in identify
        event.type = EventType.identify
        event.traits = traits
        this.addInQueue(event)
    }

    public track(eventName: string, properties: Record<string, any> = {}): void {
        if (!this.writeKey) {
            throw new Error("Analytics is not initialized. Call Analytics.init({ writeKey: 'your-key' }) before using track.");
        }
        let event = this.getDefaultEvent()
        event.type = EventType.track
        event.event = eventName
        event.properties = properties
        this.addInQueue(event)
    }

    private getAnonymousId(): string {
        let id = localStorage.getItem("analytics_anonymous_id");
        if (!id) {
            id = crypto.randomUUID()
            localStorage.setItem("analytics_anonymous_id", id);
        }
        return id;
    }

    private trackPageView(): void {
        let event = this.getDefaultEvent()
        event.type = EventType.track
        event.event = "Page View"
        event.properties = {
            url: window.location.href,
            title: document.title,
            referrer: document.referrer,
        }
        this.addInQueue(event)
    }

    // Automatically track page views
    private setupPageTracking(): void {
        window.addEventListener("DOMContentLoaded", () => this.trackPageView());
        window.addEventListener("popstate", () => this.trackPageView()); // Back/forward buttons
    }

    private addInQueue(payload: Event) {
        this.queue.push(payload);
        this.saveQueue(); // Save events locally

        if (this.queue.length >= this.batchSize) {
            this.flush();
        }
    }

    private getDefaultEvent(): Event {
        return {
            anonymousId: this.anonymousId,
            userId: this.userId || localStorage.getItem("analytics_user_id"),
            messageId: crypto.randomUUID(),
            type: EventType.invalid,
            context: {
                ...this.context,
                userAgent: navigator.userAgent,
                timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || null,
                locale: navigator.language,
                page: {
                    path: window.location.pathname,
                    referrer: document.referrer,
                    search: window.location.search,
                    title: document.title,
                    url: window.location.href,
                }
            },
            originalTimestamp: new Date().toISOString(),
            writeKey: this.writeKey
        }
    }

    private flush(): void {
        if (this.queue.length === 0) return;
        const req = { "batch": [...this.queue], "sentAt": new Date().toISOString() };
        this.queue = [];
        this.saveQueue();// Clear local storage after flush

        fetch(this.endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(req),
        })
            .then((response) => {
                if (!response.ok) {
                    return response.json().catch(() => ({})).then((errorResponse) => {
                        if (response.status >= 500 || response.status === 429) {
                            // Retry only for server errors (5xx) or Too Many Requests (429)
                            throw new Error("Retry: Server issue or rate limit reached");
                        } else {
                            console.warn("[Analytics]: Skipping event due to client error", response.status, errorResponse);
                            return;
                        }
                    });
                }
            })
            .catch((error) => {
                console.error("[Analytics]: Retry on failure", error);
                this.queue = this.queue.concat(req.batch)
                this.saveQueue();
            });
    }

    private saveQueue(): void {
        localStorage.setItem("analytics_event_queue", JSON.stringify(this.queue));
    }

    private restoreQueue(): void {
        const savedQueue = localStorage.getItem("analytics_event_queue");
        if (savedQueue) {
            this.queue = JSON.parse(savedQueue);
        }
    }

    private startFlushTimer(): void {
        if (this.flushTimer) clearInterval(this.flushTimer);
        this.flushTimer = setInterval(() => this.flush(), this.flushInterval);
    }
}

export default Analytics.getInstance();
// https://segment.com/docs/connections/spec/common/#context-fields-automatically-collected