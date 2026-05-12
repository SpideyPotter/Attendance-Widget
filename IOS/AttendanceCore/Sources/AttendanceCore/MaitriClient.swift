import Foundation

public final class MaitriClient: Sendable {
    public init() {}

    public func fetchAttendance(creds: Credentials) async throws -> AttendanceSnapshot {
        guard creds.username.contains("@") else { throw MaitriError.usernameNotEmail }
        guard !creds.password.isEmpty else { throw MaitriError.invalidCredentials }

        let cookieStorage = EphemeralCookieStorage()
        let session = Self.makeSession()
        let transport = MaitriTransport(session: session, cookieStorage: cookieStorage)

        do {
            try await transport.seedSession()
            try await transport.authenticate(creds: creds)
            let terms = try await transport.fetchTerms()
            guard let current = terms.max(by: { $0.semesterId < $1.semesterId }) else {
                throw MaitriError.noTerms
            }
            let subjects = try await transport.fetchSubjects(term: current)
            return AttendanceSnapshot(
                termName: current.name,
                termSemesterId: current.semesterId,
                subjects: subjects,
                fetchedAtMillis: Int64(Date().timeIntervalSince1970 * 1000)
            )
        } catch let error as MaitriError {
            throw error
        } catch let error as URLError {
            throw MaitriError.network(error.localizedDescription)
        } catch {
            throw MaitriError.portal(error.localizedDescription)
        }
    }

    private static func makeSession() -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 35
        configuration.httpShouldSetCookies = true
        configuration.httpCookieAcceptPolicy = .always
        return URLSession(configuration: configuration)
    }
}

private final class EphemeralCookieStorage: @unchecked Sendable {
    private let lock = NSLock()
    private var cookies: [HTTPCookie] = []

    func setCookies(_ incoming: [HTTPCookie]) {
        lock.lock()
        defer { lock.unlock() }
        let names = Set(incoming.map(\.name))
        cookies.removeAll { names.contains($0.name) }
        cookies.append(contentsOf: incoming)
    }

    func cookies(for url: URL) -> [HTTPCookie] {
        lock.lock()
        defer { lock.unlock() }
        return cookies.filter { cookie in
            guard let host = url.host else { return false }
            return host.hasSuffix(cookie.domain.trimmingCharacters(in: CharacterSet(charactersIn: ".")))
        }
    }
}

private struct MaitriTransport {
    private static let base = URL(string: "https://maitri.bmu.edu.in")!
    private static let userAgent =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    let session: URLSession
    let cookieStorage: EphemeralCookieStorage

    func seedSession() async throws {
        let url = Self.base.appendingPathComponent("loginPage.htm")
        _ = try await data(for: URLRequest(url: url), acceptHTML: true)
    }

    func authenticate(creds: Credentials) async throws {
        var request = URLRequest(url: Self.base.appendingPathComponent("j_spring_security_check"))
        request.httpMethod = "POST"
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("\(Self.base.absoluteString)/login.htm", forHTTPHeaderField: "Referer")
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let body = "j_username=\(formEncode(creds.username))&j_password=\(formEncode(creds.password))"
        request.httpBody = body.data(using: .utf8)

        let (_, response) = try await data(for: request, acceptHTML: true)
        let finalURL = response.url?.absoluteString ?? ""
        if finalURL.localizedCaseInsensitiveContains("login.htm") {
            throw MaitriError.invalidCredentials
        }
    }

    func fetchTerms() async throws -> [Term] {
        let url = Self.base.appendingPathComponent("stu_getTermsOfStudentForCourceFile.json")
        let data = try await jsonData(for: url)
        let array = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] ?? []
        return array.compactMap { object in
            guard let semesterId = object["semesterId"] as? Int else { return nil }
            return Term(
                semesterId: semesterId,
                name: object["terms2Name"] as? String ?? "Sem \(semesterId)",
                startDate: object["teachingStartDate"] as? String ?? "?",
                endDate: object["teachingEndDate"] as? String ?? "?",
                subjectwiseStudentIds: object["subjectwiseStudentIds"] as? String ?? "",
                subejctwiseBatchIds: object["subejctwiseBatchIds"] as? String ?? "",
                batchsemesterCapacity: object["batchsemesterCapacity"] as? Int ?? 0
            )
        }
    }

    func fetchSubjects(term: Term) async throws -> [Subject] {
        var components = URLComponents(
            url: Self.base.appendingPathComponent("stu_getSubjectOnChangeWithSemId1.json"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [
            URLQueryItem(name: "termId", value: String(term.semesterId)),
            URLQueryItem(name: "refreshData", value: "0"),
            URLQueryItem(name: "subjectwisestudentids", value: term.subjectwiseStudentIds),
            URLQueryItem(name: "subejctwiseBatchIds", value: term.subejctwiseBatchIds),
            URLQueryItem(name: "batchsemestercapacity", value: String(term.batchsemesterCapacity)),
        ]
        let data = try await jsonData(for: components.url!)
        let array = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] ?? []
        return array.compactMap { object in
            if let category = object["subjectCategory"] as? String, category == "PROJECT" {
                return nil
            }
            return Subject(
                code: object["subjectCode"] as? String ?? "?",
                name: decodeHtmlEntities(object["subject"] as? String ?? "?"),
                present: object["presentCount"] as? Int ?? 0,
                absent: object["absentCount"] as? Int ?? 0,
                afterCapping: object["afterCapping"] as? Double ?? 0
            )
        }
    }

    private func jsonData(for url: URL) async throws -> Data {
        var request = URLRequest(url: url)
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json, text/plain, */*", forHTTPHeaderField: "Accept")
        request.setValue("XMLHttpRequest", forHTTPHeaderField: "X-Requested-With")
        let (data, response) = try await data(for: request, acceptHTML: false)
        let finalURL = response.url?.absoluteString ?? ""
        if finalURL.localizedCaseInsensitiveContains("login.htm") {
            throw MaitriError.portal("Session was rejected when calling \(url.path)")
        }
        guard !data.isEmpty else {
            throw MaitriError.portal("Empty response body from \(url.path)")
        }
        return data
    }

    private func data(for request: URLRequest, acceptHTML: Bool) async throws -> (Data, HTTPURLResponse) {
        var request = request
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        if acceptHTML {
            request.setValue("text/html,application/json,*/*", forHTTPHeaderField: "Accept")
        }
        if let url = request.url {
            let cookies = cookieStorage.cookies(for: url)
            if !cookies.isEmpty {
                let header = cookies.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
                request.setValue(header, forHTTPHeaderField: "Cookie")
            }
        }

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw MaitriError.portal("Unexpected response type")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw MaitriError.portal("HTTP \(http.statusCode) for \(request.url?.path ?? "?")")
        }
        if let url = request.url, let headerFields = http.allHeaderFields as? [String: String] {
            let parsed = HTTPCookie.cookies(withResponseHeaderFields: headerFields, for: url)
            if !parsed.isEmpty {
                cookieStorage.setCookies(parsed)
            }
        }
        return (data, http)
    }

    private func formEncode(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private func decodeHtmlEntities(_ value: String) -> String {
        value
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&#039;", with: "'")
            .replacingOccurrences(of: "&nbsp;", with: " ")
    }
}
