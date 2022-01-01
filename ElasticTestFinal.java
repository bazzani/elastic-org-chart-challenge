/*
 * Copyright (c) 2021 Goldman Sachs.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompany this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ElasticTestFinal
{
    private static final Department CORPORATE = new Department(0, "Corporate", 0);
    private static final String EMPLOYEES_AND_DEPARTMENTS_REGEX = "\"(?:employees|departments)\":";
    private static final String INDIVIDUAL_DEPARTMENTS_REGEX = "(?:\\[ \\{ |\\[\\{| }, \\{ |}, \\{|}]}| } ] })";
    private static final String INDIVIDUAL_EMPLOYEES_REGEX = "(?:\\[ \\{ |\\[\\{| }, \\{ |}. \\{| } ],|}],)";

    String[] solution(String jsonDump) {
        Map<Integer, Department> departmentsByHead = extractDepartmentsFrom(jsonDump);
        List<Employee> employees = extractEmployeesFrom(jsonDump);

        return getSortedReportArray(departmentsByHead, employees);
    }


    private Map<Integer, Department> extractDepartmentsFrom(String jsonDump) {
        Map<Integer, Department> departmentsByHead = new HashMap();

        List<String> departments = Stream.of(jsonDump.split(EMPLOYEES_AND_DEPARTMENTS_REGEX))
                .skip(2)
                .limit(1)
                .collect(Collectors.toList());

        List<String> individualDepartments = Stream.of(departments.get(0).split(INDIVIDUAL_DEPARTMENTS_REGEX))
                .collect(Collectors.toList());

        individualDepartments.stream()
                .skip(1)
                .map(department -> {
                    int idStartIndex = department.indexOf("\"id\": ") + 6;
                    int idEndIndex = department.indexOf(",", idStartIndex);
                    int departmentId = Integer.valueOf(department.substring(idStartIndex, idEndIndex));

                    int nameStartIndex = department.indexOf("\"name\": ") + 9;
                    int nameEndIndex = department.indexOf("\",", nameStartIndex);
                    String departmentName = department.substring(nameStartIndex, nameEndIndex);

                    int headIdStartIndex = department.indexOf("\"department_head_id\": ") + 22;
                    int departmentHeadId = Integer.valueOf(department.substring(headIdStartIndex));

                    return new Department(departmentId, departmentName, departmentHeadId);
                })
                .collect(Collectors.toList())
                .forEach(department -> {
                    departmentsByHead.put(department.departmentHeadId, department);
                });

        return departmentsByHead;
    }

    private List<Employee> extractEmployeesFrom(String jsonDump) {
        final List<Employee> employeeList = new ArrayList();

        List<String> employees = Stream.of(jsonDump.split(EMPLOYEES_AND_DEPARTMENTS_REGEX))
                .skip(1)
                .limit(1)
                .collect(Collectors.toList());

        List<String> individualEmployees = Stream.of(employees.get(0).split(INDIVIDUAL_EMPLOYEES_REGEX))
                .collect(Collectors.toList());

        final int employeeTokenCountToRetrieve = individualEmployees.size() - 2;

        List<Employee> mappedEmployees = individualEmployees.stream()
                .skip(1)
                .limit(employeeTokenCountToRetrieve)
                .map(employee -> {
                    int idStartIndex = employee.indexOf("\"id\": ") + 6;
                    int idEndIndex = employee.indexOf(",", idStartIndex);
                    int employeeId = Integer.valueOf(employee.substring(idStartIndex, idEndIndex));

                    int nameStartIndex = employee.indexOf("\"name\": ") + 9;
                    int nameEndIndex = employee.indexOf("\",", nameStartIndex);
                    String employeeName = employee.substring(nameStartIndex, nameEndIndex);

                    int managerIdStartIndex = employee.indexOf("\"manager_id\": ") + 14;
                    Integer employeeManagerId;
                    if(employee.substring(managerIdStartIndex).indexOf("null") != -1) {
                        employeeManagerId = null;
                    } else {
                        employeeManagerId = Integer.valueOf(employee.substring(managerIdStartIndex));
                    }

                    return new Employee(employeeId, employeeName, employeeManagerId);
                })
                .collect(Collectors.toList());

        employeeList.addAll(mappedEmployees);

        return employeeList;
    }


    /**
     * Creates an array of CSV rows, sorted as follows:<br>
     *  1. first sort by department name in ascending order<br>
     *  2. then for each department, the first record should be the department head (or the CEO in the case of Corporate)<br>
     *  3. remaining employees in the department should be sorted by the name column in ascending order
     *
     * @param departmentsByHead map of the department head id's to department's
     * @param employeeList the flat list of all Employee's
     * @return a sorted array of CSV rows
     */
    private String[] getSortedReportArray(Map<Integer, Department> departmentsByHead, List<Employee> employeeList) {
        String[] report = new String[employeeList.size() + 1];
        int itemCount = 0;

        Map<Integer, Employee> employeesById = getEmployeeMapById(employeeList);

        List<ReportRow> reportRows = employeeList.stream()
                .map(employee -> {
                    Department department = getEmployeeDepartment(departmentsByHead, employeesById, employee);
                    String managerName = getManagerNameIfAvailable(employeesById, employee.managerId);
                    String managerNameForReport = managerName == null ? "": managerName;

                    return new ReportRow(employee.id, employee.name, department, managerNameForReport);
                })
                .sorted()
                .collect(Collectors.toList());

        String headerRow = "id,name,department,manager";
        report[itemCount] = headerRow;
        itemCount++;

        for(ReportRow row : reportRows) {
            report[itemCount] = String.format("%s,%s,%s,%s", row.id, row.name, row.department.name, row.managerName);
            itemCount++;
        }

        return report;
    }


    Map<Integer, Employee> getEmployeeMapById(List<Employee> employeeList){
        Map<Integer, Employee> employees = new HashMap();
        employeeList.forEach(employee -> employees.put(employee.id, employee));

        return employees;
    }

    String getManagerNameIfAvailable(Map<Integer, Employee> employeesById, Integer managerId) {
        Employee manager = employeesById.get(managerId);
        return manager != null ? manager.name : null;
    }

    /**
     * Recursive method that will seek for an Employee's department by looking
     * up the org chart until a department is found.<br>
     * Department heads have priority, and prevent any further searching for any manager departments further
     * up the organisation.
     *
     * @param departmentsByHead map of the department head id's to department's
     * @param employeesById map of employee id's to Employee's
     * @param employee the current Employee whose department we want, possibly the manager
     *                 of the previous Employee if called recursively
     * @return the employee's department, or the CORPORATE department if the employee does not belong to a department
     */
    Department getEmployeeDepartment(Map<Integer, Department> departmentsByHead, Map<Integer, Employee> employeesById, Employee employee) {
        Department department = null;

        boolean isADepartmentHead = departmentsByHead.containsKey(employee.id);
        Department ownDepartment = departmentsByHead.get(employee.id);
        Department managersDepartment = departmentsByHead.get(employee.managerId);

        if(isADepartmentHead) {
            department = ownDepartment;
        } else if(managersDepartment == null && employee.managerId == null) {
            department = CORPORATE;
        } else {
            Employee manager = getEmployeeById(employeesById, employee.managerId);
            department = getEmployeeDepartment(departmentsByHead, employeesById, manager);
        }

        return department;
    }

    Employee getEmployeeById(Map<Integer, Employee> employeesById, int employeeId) {
        return employeesById.get(employeeId);
    }


    private static class Employee {
        int id;
        String name;
        Integer managerId;

        private Employee(int id, String name, Integer managerId) {
            this.id = id;
            this.name = name;
            this.managerId = managerId;
        }

        public String toString() {
            return String.format("Employee [%s, %s, %s]", id, name, managerId);
        }
    }

    private static class Department {
        int id;
        String name;
        int departmentHeadId;

        private Department(int id, String name, int departmentHeadId) {
            this.id = id;
            this.name = name;
            this.departmentHeadId = departmentHeadId;
        }

        public String toString() {
            return String.format("Department [%s,%s,%s]", id, name, departmentHeadId);
        }
    }

    private static class ReportRow implements Comparable<ReportRow> {
        private int id;
        private String name;
        private Department department;
        private String managerName;

        private int isNotCeo;
        private int isNotDepartmentHead;

        private ReportRow(int id, String name, Department department, String managerName) {
            this.id = id;
            this.name = name;
            this.department = department;
            this.isNotDepartmentHead = department.departmentHeadId == id ? 0 : 1;
            this.isNotCeo = managerName.isEmpty() ? 0 : 1;
            this.managerName = managerName;
        }
        public String toString() {
            return String.format("ReportRow [%s, %s, %s--Head-%s, %s]",
                    id, name, department.name, isNotDepartmentHead == 1, managerName);
        }

        @Override
        public int compareTo(ReportRow other) {
            return Comparator.comparing((ReportRow row) -> row.department.name)
                    .thenComparing((ReportRow row) -> row.isNotCeo)
                    .thenComparing((ReportRow row) -> row.isNotDepartmentHead)
                    .thenComparing((ReportRow row) -> row.name)
                    .compare(this, other);
        }
    }
}
