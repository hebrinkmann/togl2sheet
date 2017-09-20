package de.henningbrinkmann.toggl2sheet;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.rocketbase.toggl.api.GetDetailed;
import io.rocketbase.toggl.api.TogglReportApi;
import io.rocketbase.toggl.api.TogglReportApiBuilder;
import io.rocketbase.toggl.api.model.DetailedResult;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Component
class TogglService {
    private static final Logger logger = Logger.getLogger(TogglService.class);
    private List<TogglRecord> togglRecords;
    private final Config config;

    TogglService(Config config) {
        this.config = config;
        getTogglRecords();
    }

    public void read(Reader reader) throws IOException {
        TogglCSVParser parser = new TogglCSVParser();
        String client = config.getClient();

        togglRecords = parser.parse(reader)
                .stream()
                .map(togglRecord -> togglRecord.trim(config.getTimeStep()))
                .collect(toList());
    }

    public void readFromApi() {
        TogglReportApi togglReportApi = new TogglReportApiBuilder().apiToken(config.getApiToken())
                .userAgent("toggl2sheet")
                .workspaceId(1397713)
                .build();

        GetDetailed detailed = togglReportApi.detailed()
                .since(config.getStartDate().toDate())
                .until(config.getEndDate().toDate());


        togglRecords = new ArrayList<>();

        int page = 1;
        while (true) {
            DetailedResult detailedResult = detailed.page(page).get();
            logger.info(String.format("Reading from Toggl-API: %d/%d",
                    togglRecords.size(),
                    detailedResult.getTotalCount()));
            togglRecords.addAll(detailedResult.getData()
                    .stream()
                    .map(TogglRecord::new)
                    .map(togglRecord -> togglRecord.trim(config.getTimeStep()))
                    .collect(toList()));
            page = page + 1;

            if (togglRecords.size() >= detailedResult.getTotalCount()) {
                break;
            }
        }
    }

    Map<DateTime, List<TogglRecord>> getRecordsByDay() {
        Function<TogglRecord, DateTime> keyMapper = TogglRecord::getStartDay;
        BinaryOperator<List<TogglRecord>> mergeFunction = (a, b) -> {
            a.addAll(b);
            return a;
        };
        Function<TogglRecord, List<TogglRecord>> valueMapper = togglRecord -> {
            ArrayList<TogglRecord> result = new ArrayList<>();
            result.add(togglRecord);

            return result;
        };

        return getTogglRecordStreamFiltered()
                .collect(toMap(keyMapper, valueMapper, mergeFunction));
    }

    private Stream<TogglRecord> getTogglRecordStreamFiltered() {
        return getTogglRecords().stream()
                .filter(togglRecord -> {
                    if (config.getClient() != null && !togglRecord.getClient().equals(config.getClient())) {
                        return false;
                    }

                    //noinspection RedundantIfStatement
                    if (config.getProjects() != null && !config.getProjects().contains(togglRecord.getProject())) {
                        return false;
                    }

                    return true;
                });
    }

    List<String> getProjects() {
        ArrayList<String> result = new ArrayList<>();
        result.addAll(togglRecords.stream().map(TogglRecord::getProject).collect(Collectors.toSet()));

        return result;
    }

    private List<TogglRecord> getTogglRecords() {
        if (togglRecords == null) {
            if (config.getApiToken() != null) {
                readFromApi();
            } else {
                try {
                    FileReader fileReader = new FileReader(config.getFile());
                    read(fileReader);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return togglRecords;
    }

    List<TimeSheetRecord> getTimeSheetRecords() {
        return getRecordsByDay().entrySet()
                .stream()
                .map(entry -> new TimeSheetRecord(entry.getValue()))
                .sorted((a, b) -> a.getStart().compareTo(b.getStart()))
                .collect(Collectors.toList());
    }

    String getEfforts() {
        return "Ist-Leistung: " + Util.longToHourString(getTogglRecordStreamFiltered().mapToLong(TogglRecord::getDuration)
                .sum());
    }

    String getEffortsByWeekAndProject() {
        final Map<Integer, Map<String, Long>> byWeekAndProject = getTogglRecordStreamFiltered()
                .collect(groupingBy(record -> record.getStart().getWeekOfWeekyear(),
                        groupingBy(TogglRecord::getProject, Collectors.summingLong(TogglRecord::getDuration))));


        final Map<Integer, Long> byWeek = byWeekAndProject.entrySet()
                .stream()
                .collect(groupingBy(Map.Entry::getKey,
                        Collectors.summingLong(entry1 -> entry1.getValue()
                                .values()
                                .stream()
                                .mapToLong(l -> l)
                                .sum())));

        return byWeekAndProject.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).map(entry -> {
            final String collect = entry.getValue()
                    .entrySet()
                    .stream()
                    .map(entry1 -> "  " + entry1.getKey() + ":\t" + Util.longToHourString(entry1.getValue()))
                    .collect(joining("\n"));
            return "KW " + entry.getKey() + ":\n" + collect + "\n  Gesamt:\t" + Util.longToHourString(byWeek.get(entry.getKey()));
        }).collect(joining("\n"));
    }

    String getEffortsByDayAndDescription() {
        final Map<DateTime, Map<String, Long>> byDayAndDescription = getTogglRecordStreamFiltered().collect(groupingBy(
                TogglRecord::getStartDay,
                groupingBy(TogglRecord::getDescription, Collectors.summingLong(TogglRecord::getDuration))));

        return byDayAndDescription.entrySet()
                .stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> {
                    final Stream<String> stringStream = entry.getValue()
                            .entrySet()
                            .stream()
                            .map(entry1 -> "  " + entry1.getKey() + ":\t" + Util.longToHourString(entry1.getValue()));

                    final String collect = stringStream.collect(joining("\n"));

                    return entry.getKey().toString() + "\n" + collect;
                })
                .collect(joining("\n"));
    }

    Map<DateTime, TimeSheetRecord> getTimeTimeSheetRecordsByDate() {
        Function<TimeSheetRecord, DateTime> keyFunction = timeSheetRecord -> timeSheetRecord.getStart()
                .withTimeAtStartOfDay();
        return getTimeSheetRecords().stream().collect(Collectors.toMap(keyFunction, Function.identity()));
    }

    List<TimeSheetRecord> getDateTimeSheetRecordsByDateWithMissingDays() {
        final Map<DateTime, List<TimeSheetRecord>> timeSheetRecordsByDate = getTimeSheetRecordsByDate(config.getGrouping()).stream()
                .collect(groupingBy(t -> t.getStart().withTimeAtStartOfDay()));
        final List<TimeSheetRecord> result = new ArrayList<>();
        DateTime dateTime = config.getStartDate();
        if (dateTime == null) {
            dateTime = DateTime.now().withDayOfMonth(1).withTimeAtStartOfDay();
        }
        while (!config.getEndDate().isBefore(dateTime)) {
            List<TimeSheetRecord> timeSheetRecords = timeSheetRecordsByDate.get(dateTime);
            if (timeSheetRecords == null) {
                result.add(new TimeSheetRecord(dateTime));
            } else {
                result.addAll(timeSheetRecords);
            }

            dateTime = dateTime.plusDays(1);
        }

        return result;
    }

    List<TimeSheetRecord> getTimeSheetRecordsByDate(Config.Grouping grouping) {
        Map<DateTime, Map<String, List<TogglRecord>>> collect = getTogglRecordStreamFiltered().collect(groupingBy(togglRecord -> togglRecord
                .getStart()
                .withTimeAtStartOfDay(), groupingBy(t -> {
            switch (grouping) {
                case PROJECT:
                    return t.getProject();
                case CUSTOMER:
                    return t.getClient();
                case TITLE:
                    return t.getDescription();
                default:
                    return "";
            }
        })));
        return collect.entrySet().stream().flatMap(e -> e.getValue().entrySet().stream())
                .map(e1 -> new TimeSheetRecord(e1.getValue()))
                .sorted()
                .collect(toList());
    }
}
