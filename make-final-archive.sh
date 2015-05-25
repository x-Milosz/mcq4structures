#! /bin/bash
#mvn clean package dependency:copy-dependencies
fulljar=$(ls target/*-jar-with-dependencies.jar)
normjar=${fulljar%-jar-with-dependencies.jar}.jar
version=${normjar#target/}
version=${version%.jar}

tempdir=$(mktemp -d)
destdir="${tempdir}/${version}"

mkdir "${destdir}"
cp -r target/dependency "${normjar}" "${destdir}"
cat << EOF > "${destdir}/start.bat"
java -cp "${normjar#target/};dependency/*" pl.poznan.put.gui.MainWindow
EOF
cat << EOF > "${destdir}/start.sh"
#! /bin/bash
java -cp "${normjar#target/}:dependency/*" pl.poznan.put.gui.MainWindow
EOF
chmod a+x "${destdir}/start.sh"

(cd "${tempdir}"; tar cfz "/tmp/${version}.tar.gz" "${version}")
(cd "${tempdir}"; zip -rq "/tmp/${version}.zip"    "${version}")
rm -r "${tempdir}"

ls -l "/tmp/${version}.tar.gz" "/tmp/${version}.zip"
