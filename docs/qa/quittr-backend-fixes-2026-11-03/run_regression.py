"""在独立输出目录编译并执行审计，不加载生产配置，不启动应用服务。"""
import argparse
import pathlib
import subprocess
import tempfile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--output', help='独立构建和证据目录，必须不存在或为空')
args = parser.parse_args()
audit = pathlib.Path(__file__).resolve().parent
repo = audit.parents[2]
output = pathlib.Path(args.output).resolve() if args.output else pathlib.Path(tempfile.mkdtemp(prefix='quittr-api-audit-'))
if output.exists() and any(output.iterdir()):
    raise SystemExit('输出目录非空，请指定新的空目录，避免覆盖已有文件。')
output.mkdir(parents=True, exist_ok=True)
empty = output / 'empty'
resources = output / 'test-resources'
empty.mkdir()
resources.mkdir()
(resources / 'logback-test.xml').write_text('<configuration><root level="OFF"/></configuration>\n')
ns = 'http://maven.apache.org/POM/4.0.0'
ET.register_namespace('', ns)
pom = ET.parse(repo / 'pom.xml')
build = pom.getroot().find('{'+ns+'}build')
for key, value in [('directory', output/'target'), ('sourceDirectory', repo/'src/main/java'), ('testSourceDirectory', audit/'local-tests')]:
    old = build.find('{'+ns+'}'+key)
    if old is not None:
        build.remove(old)
    ET.SubElement(build, '{'+ns+'}'+key).text = str(value)
for key, child, directory in [('resources', 'resource', empty), ('testResources', 'testResource', resources)]:
    old = build.find('{'+ns+'}'+key)
    if old is not None:
        build.remove(old)
    container = ET.SubElement(build, '{'+ns+'}'+key)
    resource = ET.SubElement(container, '{'+ns+'}'+child)
    ET.SubElement(resource, '{'+ns+'}directory').text = str(directory)
# 复用历史路由与控制器冒烟，不运行以旧缺陷为通过条件的历史行为测试。
import shutil
tests = output / 'tests'
tests.mkdir()
for source in (audit / 'local-tests').glob('*.java'):
    shutil.copy2(source, tests / source.name)
for name in ['QuittrRouteInventoryTest.java', 'QuittrEndpointContractAuditTest.java']:
    shutil.copy2(audit.parent / 'quittr-app-backend-audit-2026-11-03' / 'local-tests' / name, tests / name)
build.find('{'+ns+'}testSourceDirectory').text = str(tests)
pom.write(output/'pom.xml', encoding='unicode')
command = ['mvn', '-q', '-f', str(output/'pom.xml'), '-Daudit.repo='+str(repo), '-Daudit.output='+str(output), '-Dtest=QuittrRouteInventoryTest,QuittrEndpointContractAuditTest,QuittrPerformanceRegressionTest,QuittrJsonCompressionTest', 'test']
with (output/'execution.log').open('w') as log:
    result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT)
print('隔离审计输出：', output)
for report in sorted((output/'target/surefire-reports').glob('TEST-*.xml')):
    root = ET.parse(report).getroot()
    print(root.get('name'), {key:root.get(key) for key in ['tests','failures','errors','skipped']})
raise SystemExit(result.returncode)
