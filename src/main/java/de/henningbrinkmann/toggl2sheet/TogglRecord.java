package de.henningbrinkmann.toggl2sheet;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

class TogglRecord {
    private static final DateTimeFormatter hourFormatter = DateTimeFormat.forPattern("HH:mm");
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

    private String user;
    private String email;
    private String client;
    private String project;
    private String task;
    private String description;
    private boolean billable;
    private DateTime start;
    private DateTime end;

    TogglRecord(String[] csvRow) {
        this.user = csvRow[0];
        this.email = csvRow[1];
        this.client = csvRow[2];
        this.project = csvRow[3];
        this.task = csvRow[4];
        this.description = csvRow[5];
        this.billable = "Yes".equals(csvRow[6]);
        this.start = dateTimeFormatter.parseDateTime(csvRow[7] + " " + csvRow[8]);
        this.end = dateTimeFormatter.parseDateTime(csvRow[9] + " " + csvRow[10]);
    }

    private TogglRecord(TogglRecord other) {
        this.user = other.user;
        this.email = other.email;
        this.client = other.client;
        this.project = other.project;
        this.task = other.task;
        this.description = other.description;
        this.billable = other.billable;
        this.start = other.start;
        this.end = other.end;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TogglRecord that = (TogglRecord) o;

        if (user != null ? !user.equals(that.user) : that.user != null) return false;
        if (email != null ? !email.equals(that.email) : that.email != null) return false;
        if (client != null ? !client.equals(that.client) : that.client != null) return false;
        if (project != null ? !project.equals(that.project) : that.project != null) return false;
        if (task != null ? !task.equals(that.task) : that.task != null) return false;
        return start != null ? start.equals(that.start) : that.start == null && (end != null ? end.equals(that.end) : that.end == null);

    }

    @Override
    public int hashCode() {
        int result = user != null ? user.hashCode() : 0;
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (client != null ? client.hashCode() : 0);
        result = 31 * result + (project != null ? project.hashCode() : 0);
        result = 31 * result + (task != null ? task.hashCode() : 0);
        result = 31 * result + (start != null ? start.hashCode() : 0);
        result = 31 * result + (end != null ? end.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TogglRecord{" +
                "user='" + user + '\'' +
                ", email='" + email + '\'' +
                ", client='" + client + '\'' +
                ", project='" + project + '\'' +
                ", task='" + task + '\'' +
                ", description='" + description + '\'' +
                ", billable=" + billable +
                ", start=" + start +
                ", end=" + end +
                ", duration " + hourFormatter.print(new DateTime(getDuration(), DateTimeZone.UTC)) +
                '}';
    }

    TogglRecord trim(long step) {
        TogglRecord result = new TogglRecord(this);

        result.start = new DateTime(trimMillis(result.start.getMillis(), step));
        result.end = new DateTime(trimMillis(result.end.getMillis(), step));

        return result;
    }

    private long trimMillis(long millis, long step) {
        long rest = millis % step;

        if (rest <= step / 2) {
            return millis - rest;
        }

        return millis - rest + step;
    }

    DateTime getStartDay() {
        return start.withMillisOfDay(0);
    }

    DateTime getStart() {
        return start;
    }

    DateTime getEnd() {
        return end;
    }

    long getDuration() {
        return end.getMillis() - start.getMillis();
    }

    String getClient() {
        return client;
    }

    String getProject() {
        return project;
    }

    String getDescription() {
        return description;
    }
}
