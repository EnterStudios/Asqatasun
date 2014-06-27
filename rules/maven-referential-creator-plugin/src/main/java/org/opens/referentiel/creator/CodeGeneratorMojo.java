/*
 * To change this templateRule, choose Tools | Templates
 * and open the templateRule in the editor.
 */
package org.opens.referentiel.creator;

import org.opens.referentiel.creator.exception.I18NLanguageNotFoundException;
import org.opens.referentiel.creator.exception.InvalidParameterException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;

/**
 * @goal generate
 *
 * @author alingua
 */
public class CodeGeneratorMojo extends AbstractMojo {

    private static final String THEME_LABEL_COLUMN_NAME = "theme_";
    private static final String CRITERION_LABEL_COLUMN_NAME = "critere-label_";
    private static final String TEST_LABEL_COLUMN_NAME = "test-label_";
    private static final String THEME_CODE_COLUMN_NAME = "theme";
    private static final String CRITERION_CODE_COLUMN_NAME = "critere";
    private static final String TEST_CODE_COLUMN_NAME = "test";
    private static final String I18N_FOLDER = "-i18n";
    private static final String TESTCASES_FOLDER = "-testcases";
    private static final String ALLRESOURCES_FOLDER = "-all-resources";
    private static final String RESOURCES_FOLDER = "-resources";
    private static boolean isI18NReferentialCreated = false;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/template/rule/rule.vm"
     */
    File templateRule;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/template/testcase/testcase.vm"
     */
    File templateTestCase;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/template/unittest/unittest.vm"
     */
    File templateUnitTest;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/template/unittest/ruleimplementationtestcase.vm"
     */
    File templateRuleImplementationTestCase;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/all/ressources/descriptor.vm"
     */
    File templateDescriptor;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/all/ressources/install.vm"
     */
    File templateInstallSh;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/all/ressources/conf/webapp/beans-webapp.vm"
     */
    File templateBeansWebapp;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/all/ressources/conf/webapp/mvc/form/tgol-beans-audit-result-console-form.vm"
     */
    File templateAuditResultConsole;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/all/ressources/conf/webapp/mvc/form/tgol-beans-audit-set-up-form.vm"
     */
    File templateAuditSetUpForm;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/all/ressources/conf/webapp/export/tgol-beans-expression.vm"
     */
    File templateBeansExpression;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/pomProject/pom.vm"
     */
    File templateParentPom;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/pomProject/ref-pom.vm"
     */
    File templateRefPom;
    /**
     * @parameter
     * default-value="${basedir}/src/main/resources/pomProject/generator.vm"
     */
    File targzPom;
    /**
     * @parameter
     */
    File dataFile;
    /**
     * @parameter default-value=';'
     */
    char delimiter;
    /**
     * @parameter
     */
    String destinationFolder;
    /**
     * @parameter
     */
    String parentFolder;
    /**
     * @parameter
     */
    String referentiel;
    /**
     * @parameter
     */
    String packageName;
    /**
     * @parameter
     */
    String referentielLabel;
    /**
     * @parameter default-value=""
     */
    String refDescriptor;
    /**
     *
     */
    TreeSet<String> langSet = new TreeSet();

    /**
     * Default constructor
     */
    public CodeGeneratorMojo() {
    }

    @Override
    public void execute() {
        try {
            isContextValid();
        } catch (InvalidParameterException ipe) {
            Logger.getLogger(CodeGeneratorMojo.class.getName()).log(Level.SEVERE, null, ipe);
            return;
        }

        // Initializes engine
        VelocityEngine ve = initializeVelocity();
        Iterable<CSVRecord> records = getCsv();
        if (records == null) {
            return;
        }
        try {
            generate(ve, records);
            initializeContext();
            cleanUpUnusedFiles();
        } catch (IOException ex) {
            Logger.getLogger(CodeGeneratorMojo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ResourceNotFoundException ex) {
            Logger.getLogger(CodeGeneratorMojo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParseErrorException ex) {
            Logger.getLogger(CodeGeneratorMojo.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(CodeGeneratorMojo.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     *
     * @return
     */
    private VelocityEngine initializeVelocity() {
        Properties props = new Properties();
        props.setProperty("resource.loader", "file");
        props.setProperty("file.resource.loader.description",
                "Velocity File Resource Loader");
        props.setProperty("file.resource.loader.class",
                "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
        props.setProperty("file.resource.loader.path", "/");
        VelocityEngine ve = new VelocityEngine();
        try {
            ve.init(props);
        } catch (Exception ex) {
            Logger.getLogger(CodeGeneratorMojo.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ve;
    }

    /**
     *
     * @return
     */
    private Iterable<CSVRecord> getCsv() {
        // we parse the csv file to extract the first line and get the headers 
        LineIterator lineIterator;
        try {
            lineIterator = FileUtils.lineIterator(dataFile);
        } catch (IOException ex) {
            Logger.getLogger(CodeGeneratorMojo.class.getName()).log(Level.SEVERE, null, ex);
            lineIterator = null;
        }
        String[] csvHeaders = lineIterator.next().split(String.valueOf(delimiter));
        try {
            extractAvailableLangsFromCsvHeader(csvHeaders);
        } catch (I18NLanguageNotFoundException ex) {
            Logger.getLogger(CodeGeneratorMojo.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }

        // from here we just add each line to a build to re-create the csv content
        // without the first line.
        StringBuilder strb = new StringBuilder();
        while (lineIterator.hasNext()) {
            strb.append(lineIterator.next());
            strb.append("\n");
        }
        Reader in;
        try {
            in = new StringReader(strb.toString());
            CSVFormat csvf = CSVFormat.newFormat(delimiter).withHeader(csvHeaders);
            return csvf.parse(in);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(CodeGeneratorMojo.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        } catch (IOException ex) {
            Logger.getLogger(CodeGeneratorMojo.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    private void extractAvailableLangsFromCsvHeader(String[] csvHeaders) throws I18NLanguageNotFoundException {
        LinkedHashSet<String> themeList = new LinkedHashSet();
        LinkedHashSet<String> critereList = new LinkedHashSet();
        LinkedHashSet<String> testList = new LinkedHashSet();
        for (String header : csvHeaders) {
            if (header.startsWith(THEME_LABEL_COLUMN_NAME)) {
                themeList.add(header.split("_")[1]);
            } else if (header.startsWith(CRITERION_LABEL_COLUMN_NAME)) {
                critereList.add(header.split("_")[1]);
            } else if (header.startsWith(TEST_LABEL_COLUMN_NAME)) {
                testList.add(header.split("_")[1]);
            }
        }
        if (themeList.equals(critereList) && critereList.equals(testList)) {
            langSet.addAll(themeList);
        } else {
            throw new I18NLanguageNotFoundException("All Label on csv column must have internationalization");
        }
    }

    private void writeToI18NFile(FileGenerator fg, CSVRecord record, String lang) throws IOException {
        Integer themeIndex = Integer.valueOf(record.get(THEME_CODE_COLUMN_NAME));
        String theme = record.get(THEME_LABEL_COLUMN_NAME + lang);
        String critere = record.get(CRITERION_LABEL_COLUMN_NAME + lang);
        String critereCode = record.get(CRITERION_CODE_COLUMN_NAME);
        String test = record.get(TEST_LABEL_COLUMN_NAME + lang);
        String testCode = record.get(TEST_CODE_COLUMN_NAME);
        Map themeMap = Collections.singletonMap(themeIndex, theme);
        Map critereMap = Collections.singletonMap(critereCode, critere);
        Map testMap = Collections.singletonMap(testCode, test);
        if (StringUtils.isNotBlank(theme) && StringUtils.isNotBlank(String.valueOf(themeIndex))) {
            fg.writei18NFile(destinationFolder + I18N_FOLDER, themeMap, lang, langSet.first(), "theme", refDescriptor);
        }
        if (StringUtils.isNotBlank(critere) && StringUtils.isNotBlank(critereCode)) {
            fg.writei18NFile(destinationFolder + I18N_FOLDER, critereMap, lang, langSet.first(), "criterion", refDescriptor);
        }
        if (StringUtils.isNotBlank(test) && StringUtils.isNotBlank(testCode)) {
            fg.writei18NFile(destinationFolder + I18N_FOLDER, testMap, lang, langSet.first(), "rule", refDescriptor);
        }
        if (isI18NReferentialCreated == false) {
            fg.writei18NFile(destinationFolder + I18N_FOLDER, null, lang, langSet.first(), "referential", refDescriptor);
        }
    }

    /**
     *
     * @param ve
     * @param records
     */
    public void generate(VelocityEngine ve, Iterable<CSVRecord> records) throws IOException,
            ResourceNotFoundException, ParseErrorException, Exception {
        // Getting the Template
        Template ruleTemplate = ve.getTemplate(templateRule.getPath());
        Template parentPomTemplate = ve.getTemplate(templateParentPom.getPath());
        Template targzPomTemplate = ve.getTemplate(targzPom.getPath());
        Template refPomTemplate = ve.getTemplate(templateRefPom.getPath());
        Template webappBeansTemplate = ve.getTemplate(templateBeansWebapp.getPath());
        Template webappBeansExpressionTemplate = ve.getTemplate(templateBeansExpression.getPath());
        Template auditResultConsoleTemplate = ve.getTemplate(templateAuditResultConsole.getPath());
        Template auditSetUpFormTemplate = ve.getTemplate(templateAuditSetUpForm.getPath());
        Template testCaseTemplate = ve.getTemplate(templateTestCase.getPath());
        Template descriptorTemplate = ve.getTemplate(templateDescriptor.getPath());
        Template installTemplate = ve.getTemplate(templateInstallSh.getPath());
        Template unitTestTemplate = ve.getTemplate(templateUnitTest.getPath());
        Template ruleImplementationTestCaseTemplate = ve.getTemplate(templateRuleImplementationTestCase.getPath());
        // Create a context and add data to the templateRule placeholder
        VelocityContext context = new VelocityContext();
        // Fetch templateRule into a StringWriter
        FileGenerator fg = new FileGenerator(referentiel, referentielLabel);
        fg.createI18NFiles(langSet, destinationFolder + I18N_FOLDER);

        // we parse the records collection only once to create the i18n files.
        // These files will be then used later to create other context files
        // using the i18n keys.
        for (CSVRecord record : records) {
            for (String lang : langSet) {
                writeToI18NFile(fg, record, lang);
            }
            isI18NReferentialCreated = true;
            String test = record.get(TEST_CODE_COLUMN_NAME);
            String testLabelFr = record.get(TEST_LABEL_COLUMN_NAME + langSet.first());
            context = fg.getContextRuleClassFile(referentiel, packageName, test, testLabelFr, context);
            fg.writeFileCodeGenerate(context, ruleTemplate, destinationFolder);
            fg.writeUnitTestGenerate(context, unitTestTemplate, destinationFolder, testLabelFr);
            String[] testsCasesState = {"Passed", "Failed", "NMI", "NA"};
            for (int i = 0; i < testsCasesState.length; i++) {
                context.put("state", testsCasesState[i]);
                fg.writeTestCaseGenerate(context, testCaseTemplate, destinationFolder + TESTCASES_FOLDER, String.valueOf(i + 1));
            }
        }

        fg.createSqlReference(destinationFolder + RESOURCES_FOLDER);
        fg.createSqlTheme(destinationFolder + I18N_FOLDER, destinationFolder + RESOURCES_FOLDER);
        fg.createSqlCritere(destinationFolder + I18N_FOLDER, destinationFolder + RESOURCES_FOLDER);
        fg.createSqlTest(destinationFolder + I18N_FOLDER, destinationFolder + RESOURCES_FOLDER);
        fg.createSqlParameters(destinationFolder + I18N_FOLDER, destinationFolder + RESOURCES_FOLDER);

        fg.writeAuditSetUpFormBeanGenerate(context, auditSetUpFormTemplate, destinationFolder + ALLRESOURCES_FOLDER);
        fg.writeAuditResultConsoleBeanGenerate(context, auditResultConsoleTemplate, destinationFolder + ALLRESOURCES_FOLDER);
        fg.writeWebappBeansGenerate(context, webappBeansTemplate, destinationFolder + ALLRESOURCES_FOLDER);
        fg.writeWebappBeansExpressionGenerate(context, webappBeansExpressionTemplate, destinationFolder + ALLRESOURCES_FOLDER);
        fg.writeInstallGenerate(context, installTemplate, destinationFolder + ALLRESOURCES_FOLDER);
        fg.writeDescriptorGenerate(context, descriptorTemplate, destinationFolder + ALLRESOURCES_FOLDER);
        fg.writeRuleImplementationTestCaseGenerate(context, ruleImplementationTestCaseTemplate, destinationFolder);

        fg.adaptParentPom(context, parentPomTemplate, parentFolder);
        fg.adaptRefPom(context, refPomTemplate, destinationFolder);
        fg.adaptTargzPom(context, targzPomTemplate, destinationFolder + ALLRESOURCES_FOLDER);
    }

    /**
     * This method sets the working directory and copies the static context
     * files such as log4j or xmlDataSet (needed by HsqlDb) to the appropriate
     * path.
     *
     * @throws IOException
     */
    private void initializeContext() throws IOException {
        String workingDir = System.getProperty("user.dir");
        File dataset = FileUtils.getFile(workingDir + "/src/main/resources/dataset/emptyFlatXmlDataSet.xml");
        File log4jFile = FileUtils.getFile(workingDir + "/src/main/resources/log4j/log4j.properties");
        File datasetFolder = new File(destinationFolder + "-testcases/src/main/resources/dataSets");
        File log4jFolder = new File(destinationFolder + "/src/main/resources");
        dataset.mkdirs();
        log4jFile.mkdirs();
        FileUtils.copyFileToDirectory(dataset, datasetFolder);
        FileUtils.copyFileToDirectory(log4jFile, log4jFolder);
    }

    /**
     * Clean up uneeded files at the end of the generation (such as App.java
     * created by the maven project context creator)
     *
     * @throws IOException
     */
    private void cleanUpUnusedFiles() throws IOException {
        FileCleaner.cleanUpContext(destinationFolder);
    }

    /**
     *
     * @throws InvalidParameterException
     */
    private void isContextValid() throws InvalidParameterException {
        if (templateRule == null
                || templateRuleImplementationTestCase == null
                || templateTestCase == null
                || templateUnitTest == null
                || templateAuditResultConsole == null
                || templateAuditSetUpForm == null
                || templateBeansWebapp == null
                || templateInstallSh == null
                || templateDescriptor == null
                || String.valueOf(delimiter).isEmpty()
                || dataFile == null
                || StringUtils.isBlank(destinationFolder)
                || StringUtils.isBlank(packageName)
                || StringUtils.isBlank(referentiel)
                || StringUtils.isBlank(referentielLabel)) {
            throw new InvalidParameterException("One or several parameter(s) set to the pom file is/are invalid(s)");
        }
    }
}
