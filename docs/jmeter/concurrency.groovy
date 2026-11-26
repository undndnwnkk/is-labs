import groovy.json.JsonOutput
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.http.*
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

class WebUser {
    String base, auth, page
    def http = HttpClient.newBuilder().cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(10)).build()

    WebUser(String base, String user, String password) {
        this.base = base
        auth = 'Basic ' + "$user:$password".getBytes('UTF-8').encodeBase64()
    }
    String request(String path, String type = null, byte[] body = null) {
        def request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30))
                .header('Authorization', auth).header('Accept-Language', 'en')
        if (body != null) request.header('Content-Type', type).POST(HttpRequest.BodyPublishers.ofByteArray(body))
        def response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        assert response.statusCode() == 200 : "HTTP ${response.statusCode()}: $path"
        page = response.body()
    }
    def doc() { Jsoup.parse(page) }
    String state() { doc().select('input[name=jakarta.faces.ViewState]').first().val() }
    String post(String path, String form, String button, Map values = [:]) {
        def args = [(form): form, 'jakarta.faces.ViewState': state(), (button): button] + values
        def body = args.collect { k,v -> URLEncoder.encode(k.toString(), 'UTF-8') + '=' +
                URLEncoder.encode(v.toString(), 'UTF-8') }.join('&').getBytes('UTF-8')
        request(path, 'application/x-www-form-urlencoded', body)
    }
    Map fields(String form) {
        def result = [:]
        doc().select("form[id=$form] input[name], form[id=$form] select[name]").each {
            if (it.tagName() == 'select') {
                def option = it.select('option[selected]').first() ?: it.select('option').first()
                result[it.attr('name')] = option == null ? '' : option.attr('value')
            } else if (it.attr('type') != 'submit') result[it.attr('name')] = it.val()
        }
        result
    }
    void search(String name) {
        request('/index.xhtml')
        def args = ['routesForm': 'routesForm', 'routesForm:filter': name,
            'jakarta.faces.ViewState': state(), 'jakarta.faces.partial.ajax': 'true',
            'jakarta.faces.source': 'routesForm:filter', 'jakarta.faces.behavior.event': 'keyup',
            'jakarta.faces.partial.event': 'keyup', 'jakarta.faces.partial.execute': 'routesForm:filter',
            'jakarta.faces.partial.render': '@all']
        def body = args.collect { k,v -> URLEncoder.encode(k, 'UTF-8') + '=' + URLEncoder.encode(v, 'UTF-8') }
                .join('&').getBytes('UTF-8')
        request('/index.xhtml', 'application/x-www-form-urlencoded', body)
        if (page.contains('<partial-response')) {
            def xml = Jsoup.parse(page, '', Parser.xmlParser())
            assert xml.select('error').isEmpty() : page
            def update = xml.select('update').find { it.attr('id') == 'jakarta.faces.ViewRoot' }
            assert update != null : page
            page = update.text()
        }
    }
    List rows() { doc().select('table[id="routesForm:routesTable"] tbody tr').findAll { !it.select('a.name-link').isEmpty() } }
    int findId(String name) {
        search(name)
        def row = rows().find { it.select('a.name-link').text() == name }
        assert row != null : "Маршрут не найден: $name"
        (row.select('a.name-link').attr('href') =~ /[?&]id=(\d+)/)[0][1] as int
    }
    void openCreate() { request('/index.xhtml'); post('/index.xhtml', 'routesForm', 'routesForm:create') }
    boolean create(String name, long x, long y = 1L) {
        openCreate(); saveCreate(name, x, y)
    }
    boolean saveCreate(String name, long x, long y = 1L) {
        post('/index.xhtml', 'routesForm', 'routesForm:save', ['routesForm:name': name, 'routesForm:cx': "$x",
            'routesForm:cy': "$y", 'routesForm:from': '1', 'routesForm:to': '',
            'routesForm:distance': '5', 'routesForm:rating': '3', 'routesForm:filter': ''])
        page.contains('Маршрут создан')
    }
    void openEdit(int id) {
        request('/route-view.xhtml?id=' + id)
        post('/route-view.xhtml?id=' + id, 'detailForm', 'detailForm:edit')
    }
    boolean saveEdit(int id, int rating) {
        post('/route-view.xhtml?id=' + id, 'detailForm', 'detailForm:save', fields('detailForm') +
                ['detailForm:detailRating': "$rating"])
        page.contains('Изменения сохранены')
    }
    void openDelete(String name) {
        search(name)
        def button = rows().first().select('a[id$=askDelete]').first().id()
        post('/index.xhtml', 'routesForm', button, ['routesForm:filter': name])
    }
    boolean saveDelete(String name) {
        post('/index.xhtml', 'routesForm', 'routesForm:delete', ['routesForm:replacement': '', 'routesForm:filter': name])
        page.contains('Маршрут удалён')
    }
    String upload(List records) {
        request('/import.xhtml')
        String boundary = 'Lab2' + UUID.randomUUID().toString()
        def fields = ['importForm': 'importForm', 'jakarta.faces.ViewState': state(), 'importForm:upload': 'Импортировать']
        def parts = fields.collect { k,v -> "--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n" }.join('')
        parts += "--$boundary\r\nContent-Disposition: form-data; name=\"importForm:file\"; filename=\"routes.json\"\r\nContent-Type: application/json\r\n\r\n${JsonOutput.toJson(records)}\r\n--$boundary--\r\n"
        request('/import.xhtml', "multipart/form-data; boundary=$boundary", parts.getBytes('UTF-8'))
    }
    List history() {
        request('/import.xhtml')
        doc().select('table[id="historyForm:history"] tbody tr').findAll { it.select('td').size() > 1 }
                .collect { it.select('td').collect { cell -> cell.text() } }
    }
    int locationCount() {
        request('/locations.xhtml')
        doc().select('table tbody tr').count { it.select('td').size() > 1 }
    }
}

int worker = ctx.getThreadNum()
String run = props.get('lab2.run')
long seed = run as long
String base = props.getProperty('baseUrl', 'http://localhost:8080/routes')
def sync = { props.get('lab2.barrier').await(45, TimeUnit.SECONDS) }
def results = props.get('lab2.results')
def web = new WebUser(base, worker == 0 ? 'alice' : 'bob', worker == 0 ? 'Alice123!' : 'Bob12345!')
def admin = new WebUser(base, worker == 0 ? 'admin' : 'admin2', worker == 0 ? 'Admin123!' : 'Admin234!')
def record = { String name, long x -> [name:name, coordinates:[x:x,y:1], from:[x:1,y:2,z:3], distance:5,rating:3] }
def exactlyOne = { String key, boolean success ->
    results.put(key + worker, success)
    sync()
    assert [results.get(key+'0'), results.get(key+'1')].count { it } == 1 : "Конфликт $key: $results"
    sync()
}
def stage = 'CRUD'
try {
    String own = "JM-$run-crud-$worker"
    assert web.create(own, seed + worker)
    int id = web.findId(own)
    web.openEdit(id); assert web.saveEdit(id, 7) : web.page
    web.openDelete(own); assert web.saveDelete(own)
    web.search(own); assert web.rows().isEmpty()
    sync()

    stage = 'unique name'
    String same = "JM-$run-name"
    web.openCreate(); sync()
    boolean created = web.saveCreate(worker == 0 ? same : " ${same.toUpperCase()} ", seed + 10 + worker)
    assert created || web.page.contains('Название маршрута уже используется') : web.page
    exactlyOne('name', created)

    stage = 'unique coordinates'
    web.openCreate(); sync()
    created = web.saveCreate("JM-$run-coordinates-$worker", seed + 20)
    assert created || web.page.contains('Координаты маршрута уже используются') : web.page
    exactlyOne('coordinates', created)

    stage = 'update/update'
    String shared = "JM-$run-update"
    if (worker == 0) { assert web.create(shared, seed+30); props.put('lab2.updateId', web.findId(shared)) }
    sync()
    int sharedId = props.get('lab2.updateId') as int
    admin.openEdit(sharedId); sync()
    boolean saved = admin.saveEdit(sharedId, 10 + worker)
    assert saved || admin.page.contains('Маршрут уже изменён') : admin.page
    exactlyOne('update', saved)

    stage = 'delete/delete'
    admin.openDelete(shared); sync()
    boolean deleted = admin.saveDelete(shared)
    assert deleted || admin.page.contains('Маршрут уже удалён') : admin.page
    exactlyOne('delete', deleted)
    web.search(shared); assert web.rows().isEmpty()
    sync()

    stage = 'update/delete'
    shared = "JM-$run-mixed"
    if (worker == 0) { assert web.create(shared, seed+40); props.put('lab2.mixedId', web.findId(shared)) }
    sync()
    sharedId = props.get('lab2.mixedId') as int
    if (worker == 0) admin.openEdit(sharedId) else admin.openDelete(shared)
    sync()
    saved = worker == 0 ? admin.saveEdit(sharedId, 22) : admin.saveDelete(shared)
    assert saved || admin.page.contains('Маршрут уже изменён') || admin.page.contains('Маршрут уже удалён') : admin.page
    exactlyOne('mixed', saved)

    stage = 'import/import'
    int before = web.locationCount()
    sync()
    web.upload([record("JM-$run-import", seed+50)])
    def last = web.doc().select('table[id="historyForm:history"] tbody tr').first().select('td').collect { it.text() }
    assert last[1] in ['SUCCESS','FAILED'] : web.page
    assert last[1] != 'SUCCESS' || last[4] == '1'
    assert last[1] != 'FAILED' || (last[4] == '' && last[5].contains('уже используется'))
    exactlyOne('import', last[1] == 'SUCCESS')
    assert web.locationCount() == before + 1 : 'Импорт оставил лишние вложенные локации'
    sync()

    stage = 'import/create'
    if (worker == 0) web.openCreate()
    sync()
    if (worker == 0) saved = web.saveCreate("JM-$run-cross", seed+60)
    else {
        web.upload([record("JM-$run-cross", seed+60)])
        saved = web.doc().select('table[id="historyForm:history"] tbody tr').first().select('td')[1].text() == 'SUCCESS'
    }
    exactlyOne('cross', saved)

    stage = 'rollback'
    for (int turn = 0; turn < 2; turn++) {
        if (worker == turn) {
            before = web.locationCount()
            String rollback = "JM-$run-rollback-$worker"
            web.upload([record(rollback, seed+70+worker*2), record(rollback, seed+71+worker*2)])
            last = web.doc().select('table[id="historyForm:history"] tbody tr').first().select('td').collect { it.text() }
            assert last[1] == 'FAILED' && last[4] == '' && last[5].contains('Запись 2') : last
            assert web.locationCount() == before : 'После rollback остались вложенные локации'
            web.search(rollback); assert web.rows().isEmpty() : 'После rollback остался маршрут'
        }
        sync()
    }
    stage = 'history permissions'
    String owner = worker == 0 ? 'alice' : 'bob'
    def history = web.history()
    assert !history.isEmpty() && history.every { it[2] == owner } : history
    def owners = admin.history().collect { it[2] }
    assert owners.containsAll(['alice','bob']) : owners
    sync()
    SampleResult.setSuccessful(true)
    SampleResult.setResponseCode('200')
    SampleResult.setResponseData("PASS: CRUD, name, coordinates, update/update, delete/delete, update/delete, import/import, import/create, rollback, history ($owner)", 'UTF-8')
} catch (Throwable error) {
    props.get('lab2.barrier').reset()
    SampleResult.setSuccessful(false)
    SampleResult.setResponseCode('500')
    SampleResult.setResponseMessage("$stage: $error")
    SampleResult.setResponseData("$stage: $error\n" + (web.page ?: ''), 'UTF-8')
    log.error("Lab2 scenario failed at $stage", error)
}
