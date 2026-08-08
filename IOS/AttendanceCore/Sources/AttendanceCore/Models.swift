import Foundation

public struct Term: Equatable, Sendable {
    public let semesterId: Int
    public let name: String
    public let startDate: String
    public let endDate: String
    public let subjectwiseStudentIds: String
    public let subejctwiseBatchIds: String
    public let batchsemesterCapacity: Int

    public init(
        semesterId: Int,
        name: String,
        startDate: String,
        endDate: String,
        subjectwiseStudentIds: String,
        subejctwiseBatchIds: String,
        batchsemesterCapacity: Int
    ) {
        self.semesterId = semesterId
        self.name = name
        self.startDate = startDate
        self.endDate = endDate
        self.subjectwiseStudentIds = subjectwiseStudentIds
        self.subejctwiseBatchIds = subejctwiseBatchIds
        self.batchsemesterCapacity = batchsemesterCapacity
    }
}

public struct Subject: Equatable, Codable, Sendable, Identifiable {
    public let code: String
    public let name: String
    public let present: Int
    public let absent: Int
    public let afterCapping: Double

    public init(code: String, name: String, present: Int, absent: Int, afterCapping: Double) {
        self.code = code
        self.name = name
        self.present = present
        self.absent = absent
        self.afterCapping = afterCapping
    }

    public var id: String { code }

    public var total: Int { present + absent }

    public var percentage: Double {
        guard total > 0 else { return 0 }
        return Double(present) * 100.0 / Double(total)
    }

    public var abbreviation: String { SubjectAbbreviation.make(from: name) }
}

public struct AttendanceSnapshot: Equatable, Codable, Sendable {
    public let termName: String
    public let termSemesterId: Int
    public let subjects: [Subject]
    public let fetchedAtMillis: Int64

    public init(termName: String, termSemesterId: Int, subjects: [Subject], fetchedAtMillis: Int64) {
        self.termName = termName
        self.termSemesterId = termSemesterId
        self.subjects = subjects
        self.fetchedAtMillis = fetchedAtMillis
    }

    public var totalPresent: Int { subjects.reduce(0) { $0 + $1.present } }
    public var totalDelivered: Int { subjects.reduce(0) { $0 + $1.total } }

    public var overallPercentage: Double {
        guard totalDelivered > 0 else { return 0 }
        return Double(totalPresent) * 100.0 / Double(totalDelivered)
    }
}

public struct TimetableSession: Equatable, Codable, Sendable, Identifiable {
    public let lectureDate: String
    public let lectureDay: String
    public let startTime: String
    public let endTime: String
    public let sessionNo: String
    public let subjectName: String
    public let topic: String
    public let classRoom: String
    public let facultyName: String
    public let description: String

    public init(
        lectureDate: String,
        lectureDay: String,
        startTime: String,
        endTime: String,
        sessionNo: String,
        subjectName: String,
        topic: String,
        classRoom: String,
        facultyName: String,
        description: String
    ) {
        self.lectureDate = lectureDate
        self.lectureDay = lectureDay
        self.startTime = startTime
        self.endTime = endTime
        self.sessionNo = sessionNo
        self.subjectName = subjectName
        self.topic = topic
        self.classRoom = classRoom
        self.facultyName = facultyName
        self.description = description
    }

    public var id: String {
        "\(lectureDate)|\(startTime)|\(endTime)|\(subjectName)|\(sessionNo)"
    }
}

public struct TimetableSnapshot: Equatable, Codable, Sendable {
    public let termName: String
    public let termSemesterId: Int
    public let startDate: String
    public let endDate: String
    public let sessions: [TimetableSession]
    public let fetchedAtMillis: Int64

    public init(
        termName: String,
        termSemesterId: Int,
        startDate: String,
        endDate: String,
        sessions: [TimetableSession],
        fetchedAtMillis: Int64
    ) {
        self.termName = termName
        self.termSemesterId = termSemesterId
        self.startDate = startDate
        self.endDate = endDate
        self.sessions = sessions
        self.fetchedAtMillis = fetchedAtMillis
    }

    /// Sessions grouped by trimmed lecture date, preserving portal order.
    public var sessionsByDate: [(date: String, day: String, sessions: [TimetableSession])] {
        var order: [String] = []
        var grouped: [String: (day: String, sessions: [TimetableSession])] = [:]
        for session in sessions {
            if grouped[session.lectureDate] == nil {
                order.append(session.lectureDate)
                grouped[session.lectureDate] = (session.lectureDay, [])
            }
            grouped[session.lectureDate]?.sessions.append(session)
        }
        return order.compactMap { date in
            guard let entry = grouped[date] else { return nil }
            return (date, entry.day, entry.sessions)
        }
    }
}

public struct Credentials: Equatable, Sendable {
    public let username: String
    public let password: String

    public init(username: String, password: String) {
        self.username = username
        self.password = password
    }

    public var isValid: Bool {
        username.contains("@") && !password.isEmpty
    }
}

public enum MaitriError: Error, Equatable, Sendable {
    case invalidCredentials
    case usernameNotEmail
    case network(String)
    case portal(String)
    case noTerms

    public var localizedDescription: String {
        switch self {
        case .invalidCredentials:
            return "Invalid username or password"
        case .usernameNotEmail:
            return "Username must be the full email (e.g. you@bmu.edu.in)"
        case .network(let message):
            return "Network error: \(message)"
        case .portal(let message):
            return message
        case .noTerms:
            return "No enrolled terms found for this account"
        }
    }
}
